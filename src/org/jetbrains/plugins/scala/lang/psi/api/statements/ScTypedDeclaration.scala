package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import _root_.org.jetbrains.plugins.scala.lang.psi.types.{ScType, Nothing}
import base.types.ScTypeElement
import toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types.Any

trait ScTypedDeclaration extends ScDeclaration {
  def typeElement : Option[ScTypeElement]
  def calcType : ScType = typeElement match {
    case Some(te) => te.cachedType.unwrap(Any)
    case None => Nothing
  }
  def declaredElements : Seq[ScTypedDefinition]
}