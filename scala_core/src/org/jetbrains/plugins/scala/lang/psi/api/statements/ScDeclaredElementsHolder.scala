package org.jetbrains.plugins.scala.lang.psi.api.statements

import toplevel.ScTyped

trait ScDeclaredElementsHolder extends ScalaPsiElement {
  def declaredElements : Seq[ScTyped]
}