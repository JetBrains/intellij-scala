package org.jetbrains.jps.incremental.scala

import data.CompilerData
import java.io.File
import sbt.inc.AnalysisStore
import org.jetbrains.jps.incremental.MessageHandler

/**
 * @author Pavel Fatin
 */
class CachingFactory(delegate: CompilerFactory, compilersLimit: Int, analysisLimit: Int) extends CompilerFactory {
  private val compilerCache = new Cache[CompilerData, Compiler](compilersLimit)

  private val analysisCache = new Cache[File, AnalysisStore](analysisLimit)

  def createCompiler(compilerData: CompilerData,
                     storeProvider: (File) => AnalysisStore,
                     messageHandler: MessageHandler): Compiler = {

    def cachingStoreProvider(file: File) = analysisCache.getOrUpdate(file)(storeProvider(file))

    compilerCache.getOrUpdate(compilerData) {
      delegate.createCompiler(compilerData, cachingStoreProvider, messageHandler)
    }
  }
}
