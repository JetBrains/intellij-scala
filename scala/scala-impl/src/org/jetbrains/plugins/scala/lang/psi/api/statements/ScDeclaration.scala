package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

trait ScDeclarationBase extends ScalaPsiElementBase { this: ScDeclaration =>
  def declaredElements : Seq[ScNamedElement]
}