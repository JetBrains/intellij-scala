package org.jetbrains.plugins.scala.lang.psi.api.statements

import toplevel.ScNamedElement

trait ScDeclaration extends ScalaPsiElement {
  def declaredElements : Seq[ScNamedElement]
}