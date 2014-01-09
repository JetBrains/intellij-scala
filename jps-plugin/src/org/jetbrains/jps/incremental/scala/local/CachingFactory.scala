package org.jetbrains.jps.incremental.scala
package local

import org.jetbrains.jps.incremental.scala.data.{CompilerJars, SbtData, CompilerData}
import java.io.File
import sbt.inc.AnalysisStore
import sbt.compiler.AnalyzingCompiler

/**
 * @author Pavel Fatin
 */
class CachingFactory(delegate: CompilerFactory, compilersLimit: Int, analysisLimit: Int, scalacLimit: Int) extends CompilerFactory {
  private val compilerCache = new Cache[CompilerData, Compiler](compilersLimit)

  private val analysisCache = new Cache[File, AnalysisStore](analysisLimit)

  private val scalacCache = new Cache[(SbtData, Option[CompilerJars]), Option[AnalyzingCompiler]](scalacLimit)

  def createCompiler(compilerData: CompilerData, client: Client, fileToStore: File => AnalysisStore): Compiler = {
    val cachingFileToStore = (file: File) => analysisCache.getOrUpdate(file)(fileToStore(file))

    compilerCache.getOrUpdate(compilerData) {
      delegate.createCompiler(compilerData, client, cachingFileToStore)
    }
  }

  def getScalac(sbtData: SbtData, compilerJars: Option[CompilerJars], client: Client): Option[AnalyzingCompiler] = {
    scalacCache.getOrUpdate((sbtData, compilerJars)) {
      delegate.getScalac(sbtData, compilerJars, client)
    }
  }
}
