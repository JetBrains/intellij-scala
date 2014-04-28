package org.jetbrains.jps.incremental.scala
package local

import org.jetbrains.jps.incremental.scala.data.{CompilerJars, SbtData, CompilerData}
import java.io.File
import sbt.inc.AnalysisStore
import sbt.compiler.AnalyzingCompiler

/**
 * @author Pavel Fatin
 */
trait CompilerFactory {
  def createCompiler(compilerData: CompilerData, client: Client, fileToStore: File => AnalysisStore): Compiler

  def getScalac(sbtData: SbtData, compilerJars: Option[CompilerJars], client: Client): Option[AnalyzingCompiler]
}
