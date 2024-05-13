package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

trait ScTypeBoundsOwner extends ScalaPsiElement {
  def lowerBound: TypeResult
  def upperBound: TypeResult

  def lowerTypeElement: Option[ScTypeElement] = None
  def upperTypeElement: Option[ScTypeElement] = None

  def hasBounds: Boolean = lowerTypeElement.nonEmpty || upperTypeElement.nonEmpty
}
