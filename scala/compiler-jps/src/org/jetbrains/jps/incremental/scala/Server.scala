package org.jetbrains.jps.incremental.scala

import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import org.jetbrains.jps.incremental.scala.data.{CompilationData, CompilerData, SbtData}

/**
 * @author Pavel Fatin
 */
trait Server {
  def compile(sbtData: SbtData, compilerData: CompilerData, compilationData: CompilationData, client: Client): ExitCode
}