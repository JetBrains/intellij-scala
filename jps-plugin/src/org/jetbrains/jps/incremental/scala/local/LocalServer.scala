package org.jetbrains.jps.incremental.scala
package local

import data.SbtData
import java.io.File
import sbt.inc.{AnalysisFormats, FileBasedStore, AnalysisStore}

/**
 * @author Pavel Fatin
 */
class LocalServer extends Server {
  def compile(arguments: ServerArguments, client: Client) {
    val compilerFactory = LocalServer.compilerFactory(arguments.sbtData)

    client.progress("Instantiating compiler...")
    val compiler = compilerFactory.createCompiler(arguments.compilerData, client, LocalServer.createAnalysisStore)

    if (!client.isCanceled) {
      client.progress("Searching for changed files...")
      compiler.compile(arguments.compilationData, client)
    }
  }
}

object LocalServer {
  // Globally cached instance of CompilerFactory
  private var cachedCompilerFactory: Option[CompilerFactory] = None

  private def compilerFactory(sbtData: SbtData): CompilerFactory = cachedCompilerFactory.getOrElse {
    val factory = new CachingFactory(new CompilerFactoryImpl(sbtData), 5, 5)
    cachedCompilerFactory = Some(factory)
    factory
  }

  private def createAnalysisStore(cacheFile: File): AnalysisStore = {
    import sbinary.DefaultProtocol.{immutableMapFormat, immutableSetFormat, StringFormat, tuple2Format}
    import sbt.inc.AnalysisFormats._
    val store = FileBasedStore(cacheFile)(AnalysisFormats.analysisFormat, AnalysisFormats.setupFormat)
    AnalysisStore.sync(AnalysisStore.cached(store))
  }
}