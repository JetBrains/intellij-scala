package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import base.types.ScTypeElement
import psi.ScalaPsiElement
import lang.psi.types.ScType

trait ScTypeBoundsOwner extends ScalaPsiElement {
  def lowerBound: ScType
  def upperBound: ScType
  def viewBound: Option[ScType] = None

  def upperTypeElement: Option[ScTypeElement] = None //todo: override in other places
  def lowerTypeElement: Option[ScTypeElement] = None //todo: override in other places
  def viewTypeElement: Option[ScTypeElement] = None
}