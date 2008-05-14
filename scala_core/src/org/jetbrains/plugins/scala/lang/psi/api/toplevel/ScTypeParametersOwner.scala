package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam

trait ScTypeParametersOwner extends ScalaPsiElement {
  def typeParameters() : Seq[ScTypeParam]
}