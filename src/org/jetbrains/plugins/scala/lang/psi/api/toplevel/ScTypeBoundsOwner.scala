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
  def viewBound: List[ScType] = Nil
  def contextBound: List[ScType] = Nil
  def hasImplicitBound: Boolean = viewTypeElement.nonEmpty || contextBoundTypeElement.nonEmpty

  def upperTypeElement: Option[ScTypeElement] = None
  def lowerTypeElement: Option[ScTypeElement] = None
  def viewTypeElement: List[ScTypeElement] = Nil
  def contextBoundTypeElement: List[ScTypeElement] = Nil

  def removeImplicitBounds() {}
}