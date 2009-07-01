package org.jetbrains.plugins.scala.lang.psi.api.statements

import _root_.org.jetbrains.plugins.scala.lang.psi.types.{ScType, Nothing}
import base.types.ScTypeElement
import toplevel.ScTyped

trait ScTypedDeclaration extends ScDeclaration {
  def typeElement : Option[ScTypeElement]
  def calcType : ScType = typeElement match {
    case Some(te) => te.cashedType
    case None => Nothing
  }
  def declaredElements : Seq[ScTyped]
}