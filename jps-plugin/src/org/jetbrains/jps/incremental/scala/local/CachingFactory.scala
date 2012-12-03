package org.jetbrains.jps.incremental.scala
package local

import data.CompilerData
import java.io.File
import sbt.inc.AnalysisStore

/**
 * @author Pavel Fatin
 */
class CachingFactory(delegate: CompilerFactory, compilersLimit: Int, analysisLimit: Int) extends CompilerFactory {
  private val compilerCache = new Cache[CompilerData, Compiler](compilersLimit)

  private val analysisCache = new Cache[File, AnalysisStore](analysisLimit)

  def createCompiler(compilerData: CompilerData, client: Client, fileToStore: File => AnalysisStore): Compiler = {
    val cachingFileToStore = (file: File) => analysisCache.getOrUpdate(file)(fileToStore(file))

    compilerCache.getOrUpdate(compilerData) {
      delegate.createCompiler(compilerData, client, cachingFileToStore)
    }
  }
}
