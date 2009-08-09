package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import toplevel.ScNamedElement

trait ScDeclaration extends ScalaPsiElement {
  def declaredElements : Seq[ScNamedElement]
}