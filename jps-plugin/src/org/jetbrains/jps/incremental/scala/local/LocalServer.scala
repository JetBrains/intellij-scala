package org.jetbrains.jps.incremental.scala
package local

import data._
import java.io.File
import sbt.inc.{FileBasedStore, AnalysisStore}
import sbt.inc.AnalysisFormats._
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode

/**
 * @author Pavel Fatin
 */
class LocalServer extends Server {
  private var cachedCompilerFactory: Option[CompilerFactory] = None
  private val lock = new Object()

  def compile(sbtData: SbtData, compilerData: CompilerData, compilationData: CompilationData, client: Client): ExitCode = {
    val compiler = lock.synchronized {
      val compilerFactory = compilerFactoryFrom(sbtData)

      client.progress("Instantiating compiler...")
      compilerFactory.createCompiler(compilerData, client, LocalServer.createAnalysisStore)
    }

    if (!client.isCanceled) {
      compiler.compile(compilationData, client)
    }

    client.compilationEnd()
    ExitCode.OK
  }

  private def compilerFactoryFrom(sbtData: SbtData): CompilerFactory = cachedCompilerFactory.getOrElse {
    val factory = new CachingFactory(new CompilerFactoryImpl(sbtData), 5, 5, 5)
    cachedCompilerFactory = Some(factory)
    factory
  }
}

object LocalServer {
  private def createAnalysisStore(cacheFile: File): AnalysisStore = {
    import sbinary.DefaultProtocol.{immutableMapFormat, immutableSetFormat, StringFormat, tuple2Format} //need for implicits
    import sbt.inc.AnalysisFormats._
    val store = FileBasedStore(cacheFile)
    AnalysisStore.sync(AnalysisStore.cached(store))
  }
}