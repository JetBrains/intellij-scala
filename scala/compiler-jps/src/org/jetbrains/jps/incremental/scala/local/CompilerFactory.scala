package org.jetbrains.jps.incremental.scala
package local

import java.io.File

import org.jetbrains.plugins.scala.compiler.data.{CompilerData, CompilerJars, SbtData}
import sbt.internal.inc.AnalyzingCompiler
import xsbti.compile.AnalysisStore

trait CompilerFactory {
  def createCompiler(compilerData: CompilerData, client: Client, fileToStore: File => AnalysisStore): Compiler

  def getScalac(sbtData: SbtData, compilerJars: Option[CompilerJars], client: Client): Option[AnalyzingCompiler]
}
