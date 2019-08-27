package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

@deprecated("to be merged with ScDeclaredElementsHolder")
trait ScDeclaration extends ScalaPsiElement {
  def declaredElements : Seq[ScNamedElement]
}