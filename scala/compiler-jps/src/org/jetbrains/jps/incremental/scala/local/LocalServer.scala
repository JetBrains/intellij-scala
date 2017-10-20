package org.jetbrains.jps.incremental.scala
package local

import java.io.File

import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import org.jetbrains.jps.incremental.scala.data._
import xsbti.compile.AnalysisStore
import sbt.internal.inc.FileAnalysisStore
/**
 * @author Pavel Fatin
 */
class LocalServer extends Server {
  private var cachedCompilerFactory: Option[CompilerFactory] = None
  private val lock = new Object()

  def compile(sbtData: SbtData, compilerData: CompilerData, compilationData: CompilationData, client: Client): ExitCode = {
    val compiler = try lock.synchronized {
      val compilerFactory = compilerFactoryFrom(sbtData)

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

  private def compilerFactoryFrom(sbtData: SbtData): CompilerFactory = cachedCompilerFactory.getOrElse {
    val factory = new CachingFactory(new CompilerFactoryImpl(sbtData), 10, 600, 10)
    cachedCompilerFactory = Some(factory)
    factory
  }
}

object LocalServer {
  private def createAnalysisStore(cacheFile: File): AnalysisStore = {
    val store = FileAnalysisStore.binary(cacheFile)
    AnalysisStore.getThreadSafeStore(AnalysisStore.getCachedStore(store))
  }
}