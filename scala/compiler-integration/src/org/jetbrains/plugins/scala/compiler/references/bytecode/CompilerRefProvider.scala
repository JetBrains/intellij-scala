package org.jetbrains.plugins.scala.compiler.references.bytecode

import org.jetbrains.jps.backwardRefs.CompilerRef

private trait CompilerRefProvider[From] extends (From => CompilerRef) {
  def toCompilerRef(from: From): CompilerRef

  override def apply(from: From): CompilerRef = toCompilerRef(from)
}
