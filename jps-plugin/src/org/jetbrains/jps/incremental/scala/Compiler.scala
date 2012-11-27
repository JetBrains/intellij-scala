package org.jetbrains.jps.incremental.scala

import data.CompilationData

/**
 * @author Pavel Fatin
 */
trait Compiler {
  def compile(compilationData: CompilationData, client: Client)
}
