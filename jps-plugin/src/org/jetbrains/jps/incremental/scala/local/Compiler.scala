package org.jetbrains.jps.incremental.scala
package local

import data.CompilationData

/**
 * @author Pavel Fatin
 */
trait Compiler {
  def compile(compilationData: CompilationData, client: Client)
}
