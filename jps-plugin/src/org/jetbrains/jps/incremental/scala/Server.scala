package org.jetbrains.jps.incremental.scala

import data.{CompilationData, CompilerData, SbtData}
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode

/**
 * @author Pavel Fatin
 */
trait Server {
  def compile(sbtData: SbtData, compilerData: CompilerData, compilationData: CompilationData, client: Client): ExitCode
}