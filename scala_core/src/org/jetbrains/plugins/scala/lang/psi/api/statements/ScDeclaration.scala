package org.jetbrains.plugins.scala.lang.psi.api.statements

trait ScDeclaration extends ScalaPsiElement {
  def names : Seq[String]
}