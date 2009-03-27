package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import psi.ScalaPsiElement
import lang.psi.types.ScType

trait ScTypeBoundsOwner extends ScalaPsiElement {
  def lowerBound() : ScType
  def upperBound() : ScType
  def viewBound: Option[ScType] = None
}