package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

trait ScDeclaration extends ScalaPsiElement {
  def declaredElements : collection.Seq[ScNamedElement]
}