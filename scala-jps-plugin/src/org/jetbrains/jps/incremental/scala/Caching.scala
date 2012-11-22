package org.jetbrains.jps.incremental.scala

import java.io.File
import sbt.inc.AnalysisStore
import org.jetbrains.jps.incremental.MessageHandler

/**
 * @author Pavel Fatin
 */
trait Caching extends CompilerFactory {
  val compilerCache = new Cache[CompilerConfiguration, Compiler](5)

  val analysisCache = new Cache[File, AnalysisStore](5)

  abstract override def createCompiler(configuration: CompilerConfiguration,
                                       storeProvider: (File) => AnalysisStore,
                                       messageHandler: MessageHandler): Compiler = {

    def cachingStoreProvider(file: File) = analysisCache.getOrUpdate(file)(storeProvider(file))

    compilerCache.getOrUpdate(configuration) {
      super.createCompiler(configuration, cachingStoreProvider, messageHandler)
    }
  }
}
