package org.jetbrains.plugins.scala.findUsages.compilerReferences

import org.jetbrains.jps.backwardRefs.CompilerRef

private trait CompilerRefProvider[From] {
  def toCompilerRef(from: From): Option[CompilerRef]
}
