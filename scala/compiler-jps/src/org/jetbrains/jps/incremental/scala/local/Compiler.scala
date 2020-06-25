package org.jetbrains.jps.incremental.scala
package local

import org.jetbrains.plugins.scala.compiler.data.CompilationData

/**
 * @author Pavel Fatin
 */
trait Compiler {
  def compile(compilationData: CompilationData, client: Client): Unit
}
