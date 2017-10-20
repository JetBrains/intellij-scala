package org.jetbrains.jps.incremental.scala
package local

import org.jetbrains.jps.incremental.scala.data.CompilationData

/**
 * @author Pavel Fatin
 */
trait Compiler {
  def compile(compilationData: CompilationData, client: Client)
}
