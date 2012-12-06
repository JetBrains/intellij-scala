package org.jetbrains.jps.incremental.scala
package local

import data._
import java.io.File
import sbt.inc.{AnalysisFormats, FileBasedStore, AnalysisStore}
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode

/**
 * @author Pavel Fatin
 */
class LocalServer extends Server {
  private var cachedCompilerFactory: Option[CompilerFactory] = None

  def compile(sbtData: SbtData, compilerData: CompilerData, compilationData: CompilationData, client: Client): ExitCode = {
    val compilerFactory = compilerFactoryFrom(sbtData)

    client.progress("Instantiating compiler...")
    val compiler = compilerFactory.createCompiler(compilerData, client, LocalServer.createAnalysisStore)

    if (!client.isCanceled) {
      client.progress("Searching for changed files...")
      compiler.compile(compilationData, client)
    }

    ExitCode.OK
  }

  private def compilerFactoryFrom(sbtData: SbtData): CompilerFactory = cachedCompilerFactory.getOrElse {
    val factory = new CachingFactory(new CompilerFactoryImpl(sbtData), 5, 5)
    cachedCompilerFactory = Some(factory)
    factory
  }
}

object LocalServer {
  private def createAnalysisStore(cacheFile: File): AnalysisStore = {
    import sbinary.DefaultProtocol.{immutableMapFormat, immutableSetFormat, StringFormat, tuple2Format}
    import sbt.inc.AnalysisFormats._
    val store = FileBasedStore(cacheFile)(AnalysisFormats.analysisFormat, AnalysisFormats.setupFormat)
    AnalysisStore.sync(AnalysisStore.cached(store))
  }
}