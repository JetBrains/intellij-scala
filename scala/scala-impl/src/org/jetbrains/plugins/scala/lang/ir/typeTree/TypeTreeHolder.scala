package org.jetbrains.plugins.scala.lang.ir.typeTree

import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

trait TypeTreeHolder extends Typeable {
  def typeTree: TypeTree

  def toScalaCode: String = typeTree.toScalaCode
}
