package org.jetbrains.jps.incremental.scala
package local

import java.io.File
import java.util.ServiceLoader

import com.intellij.openapi.diagnostic.{Logger => JpsLogger}
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import org.jetbrains.plugins.scala.compiler.data.{CompilationData, CompilerData, SbtData}
import sbt.internal.inc.FileAnalysisStore
import xsbti.compile.AnalysisStore

import scala.jdk.CollectionConverters._

/**
 * @author Pavel Fatin
 */
class LocalServer extends Server {

  import LocalServer._

  private var cachedCompilerFactory: Option[CompilerFactory] = None
  private val lock = new Object()

  override def compile(sbtData: SbtData,
              compilerData: CompilerData,
              compilationData: CompilationData,
              client: Client): ExitCode = {
    val collectingSourcesClient = new DelegateClient(client) with CollectingSourcesClient
    val compiler = try lock.synchronized {
      val compilerFactory = compilerFactoryFrom(sbtData, compilerData)

      collectingSourcesClient.progress("Instantiating compiler...")
      compilerFactory.createCompiler(compilerData, collectingSourcesClient, LocalServer.createAnalysisStore)
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

    ExitCode.OK
  }

  private def compilerFactoryFrom(sbtData: SbtData, compilerData: CompilerData): CompilerFactory = cachedCompilerFactory.getOrElse {
    val cf = ServiceLoader.load(classOf[CompilerFactoryService])
    val registeredCompilerFactories = cf.iterator().asScala.toList
    Log.info(s"Registered factories of ${classOf[CompilerFactoryService].getName}: $registeredCompilerFactories")
    val firstEnabledCompilerFactory = registeredCompilerFactories.find(_.isEnabled(compilerData))
    Log.info(s"First enabled factory (if any): $firstEnabledCompilerFactory")
    val factory = new CachingFactory(firstEnabledCompilerFactory.map(_.get(sbtData)).getOrElse(new CompilerFactoryImpl(sbtData)), 10, 600, 10)
    cachedCompilerFactory = Some(factory)
    factory
  }
}

object LocalServer {
  private val Log: JpsLogger = JpsLogger.getInstance(LocalServer.getClass.getName)
  private def createAnalysisStore(cacheFile: File): AnalysisStore = {
    val store = FileAnalysisStore.binary(cacheFile)
    AnalysisStore.getThreadSafeStore(AnalysisStore.getCachedStore(store))
  }

  private trait CollectingSourcesClient extends Client {

    var sources = Set.empty[File]

    abstract override def generated(source: File, module: File, name: String): Unit = {
      super.generated(source, module, name)
      sources += source
    }
  }
}