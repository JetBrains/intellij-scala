package org.jetbrains.jps.incremental.scala.local

import org.jetbrains.jps.incremental.scala.local.zinc.AnalysisStoreFactory
import org.jetbrains.jps.incremental.scala.{Client, CompileServerBundle, DelegateClient, ExitCode, Server}
import org.jetbrains.plugins.scala.compiler.data.{CompilationData, CompilerData, SbtData}
import sbt.internal.inc.{Analysis, PlainVirtualFileConverter, Stamper}
import xsbti.compile.{AnalysisContents, AnalysisStore}

import java.io.File
import java.nio.file.Path
import java.util.ServiceLoader
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

final class LocalServer extends Server {

  import LocalServer._

  private var cachedCompilerFactory: Option[CompilerFactory] = None
  private val lock = new Object()

  override def compile(
    sbtData: SbtData,
    compilerData: CompilerData,
    compilationData: CompilationData,
    client: Client
  ): Either[Server.ServerError, ExitCode] =
    Right(doCompile(sbtData, compilerData, compilationData, client))

  def doCompile(
    sbtData: SbtData,
    compilerData: CompilerData,
    compilationData: CompilationData,
    client: Client
  ): ExitCode = {
    val collectingSourcesClient = new DelegateClient(client) with CollectingSourcesClient
    val compiler = try lock.synchronized {
      val compilerFactory = compilerFactoryFrom(sbtData, compilerData, client)

      collectingSourcesClient.progress(CompileServerBundle.message("instantiating.compiler"))
      val fileToStore: File => AnalysisStore = (AnalysisStoreFactory.createAnalysisStore _).compose(_.toPath)
      compilerFactory.createCompiler(compilerData, collectingSourcesClient, fileToStore)
    } catch {
      case e: Throwable =>
        compilationData.sources.foreach(f => collectingSourcesClient.sourceStarted(f.toString))
        throw e
    }

    if (!collectingSourcesClient.isCanceled) {
      client.compilationStart()
      compiler.compile(compilationData, collectingSourcesClient)
      client.compilationEnd(collectingSourcesClient.sources ++ compilationData.sources)
    }

    ExitCode.Ok
  }

  override def computeStamps(outputFiles: Seq[Path], analysisFile: Path, client: Client): Right[Server.ServerError, ExitCode] = {
    val analysisStore = AnalysisStoreFactory.createAnalysisStore(analysisFile)

    analysisStore.get().toScala.filter(_.getAnalysis.isInstanceOf[Analysis]).foreach { analysisContents =>
      val analysis = analysisContents.getAnalysis.asInstanceOf[Analysis]
      var newStamps = analysis.stamps
      val stamper = Stamper.forHashInRootPaths(PlainVirtualFileConverter.converter)
      outputFiles.foreach { classFile =>
        val virtualFile = PlainVirtualFileConverter.converter.toVirtualFile(classFile)
        val stamp = stamper(virtualFile)
        newStamps = newStamps.markProduct(virtualFile, stamp)
      }
      val newAnalysis = analysis.copy(stamps = newStamps)
      val newAnalysisContents = AnalysisContents.create(newAnalysis, analysisContents.getMiniSetup)
      analysisStore.set(newAnalysisContents)
    }

    Right(ExitCode.Ok)
  }

  // NOTE: `LocalServer` can be used both in JPS process (when can't connect to the scala compile server)
  // and in ScalaCompileServer process. We need to use client.internalInfo instead of just Log,
  // because when run in SCS `Log.info` doesn't do anything (it uses DefaultLogger, which is NoOp)
  private def compilerFactoryFrom(
    sbtData: SbtData,
    compilerData: CompilerData,
    client: Client
  ): CompilerFactory = cachedCompilerFactory.getOrElse {
    val cf = ServiceLoader.load(classOf[CompilerFactoryService])
    val registeredCompilerFactories = cf.iterator().asScala.toList
    client.internalInfo(s"Registered factories of ${classOf[CompilerFactoryService].getName}: $registeredCompilerFactories")
    val firstEnabledCompilerFactory = registeredCompilerFactories.find(_.isEnabled(compilerData))
    client.internalInfo(s"First enabled factory (if any): $firstEnabledCompilerFactory")
    val factory = new CachingFactory(firstEnabledCompilerFactory.map(_.get(sbtData)).getOrElse(new CompilerFactoryImpl(sbtData)), 10, 10)
    cachedCompilerFactory = Some(factory)
    factory
  }
}

object LocalServer {
  private trait CollectingSourcesClient extends Client {

    var sources = Set.empty[File]

    abstract override def generated(source: File, module: File, name: String): Unit = {
      super.generated(source, module, name)
      sources += source
    }
  }
}
