package org.jetbrains.jps.incremental.scala

import data.CompilerData
import java.io.File
import sbt.inc.AnalysisStore

/**
 * @author Pavel Fatin
 */
trait CompilerFactory {
  def createCompiler(compilerData: CompilerData, client: Client, fileToStore: File => AnalysisStore): Compiler
}
