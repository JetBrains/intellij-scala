package org.jetbrains.jps.incremental.scala
package local

import java.io.File
import java.util.ServiceLoader

import com.intellij.openapi.diagnostic.{Logger => JpsLogger}
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import org.jetbrains.jps.incremental.scala.data._
import xsbti.compile.AnalysisStore
import sbt.internal.inc.FileAnalysisStore

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class LocalServer extends Server {
  private var cachedCompilerFactory: Option[CompilerFactory] = None
  private val lock = new Object()

  def compile(sbtData: SbtData, compilerData: CompilerData, compilationData: CompilationData, client: Client): ExitCode = {
    val compiler = try lock.synchronized {
      val compilerFactory = compilerFactoryFrom(sbtData, compilerData)

      client.progress("Instantiating compiler...")
      compilerFactory.createCompiler(compilerData, client, LocalServer.createAnalysisStore)
    } catch {
      case e: Throwable =>
        compilationData.sources.foreach(f => client.sourceStarted(f.toString))
        throw e
    }

    if (!client.isCanceled) {
      compiler.compile(compilationData, client)
    }

    client.compilationEnd()
    ExitCode.OK
  }

  private def compilerFactoryFrom(sbtData: SbtData, compilerData: CompilerData): CompilerFactory = cachedCompilerFactory.getOrElse {
    val cf = ServiceLoader.load(classOf[CompilerFactoryService])
    val registeredCompilerFactories = cf.iterator().asScala.toList
    LocalServer.Log.info(s"Registered factories of ${classOf[CompilerFactoryService].getName}: $registeredCompilerFactories")
    val firstEnabledCompilerFactory = registeredCompilerFactories.find(_.isEnabled(compilerData))
    LocalServer.Log.info(s"First enabled factory (if any): $firstEnabledCompilerFactory")
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
}