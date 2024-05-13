package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType

trait ScImplicitBoundsOwner extends ScalaPsiElement {
  def viewBound: Seq[ScType]    = Nil
  def contextBound: Seq[ScType] = Nil

  def viewTypeElement: Seq[ScTypeElement]         = Nil
  def contextBoundTypeElement: Seq[ScTypeElement] = Nil

  def hasImplicitBounds: Boolean = viewTypeElement.nonEmpty || contextBoundTypeElement.nonEmpty

  def removeImplicitBounds(): Unit = {}
}
