package org.jetbrains.plugins.scala.lang.psi.api.statements

import base.types.ScTypeElement
import types.Nothing
import toplevel.ScTyped

trait ScTypedDeclaration extends ScDeclaration {
  def typeElement : Option[ScTypeElement]
  def calcType = typeElement match {
    case Some(te) => te.getType
    case None => Nothing
  }
  def declaredElements : Seq[ScTyped]
}