package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

trait ScDeclaration extends ScalaPsiElement {
  def declaredElements : Seq[ScNamedElement]
}