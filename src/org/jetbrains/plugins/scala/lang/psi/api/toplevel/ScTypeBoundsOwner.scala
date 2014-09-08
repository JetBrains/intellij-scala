package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

trait ScTypeBoundsOwner extends ScalaPsiElement {
  def lowerBound: TypeResult[ScType]
  def upperBound: TypeResult[ScType]
  def viewBound: Seq[ScType] = Nil
  def contextBound: Seq[ScType] = Nil
  def hasImplicitBound: Boolean = viewTypeElement.nonEmpty || contextBoundTypeElement.nonEmpty

  def upperTypeElement: Option[ScTypeElement] = None
  def lowerTypeElement: Option[ScTypeElement] = None
  def viewTypeElement: Seq[ScTypeElement] = Nil
  def contextBoundTypeElement: Seq[ScTypeElement] = Nil

  def removeImplicitBounds() {}
}