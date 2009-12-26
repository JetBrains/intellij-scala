package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import base.types.ScTypeElement
import psi.ScalaPsiElement
import lang.psi.types.ScType
import types.result.TypeResult

trait ScTypeBoundsOwner extends ScalaPsiElement {
  def lowerBound: TypeResult[ScType]
  def upperBound: TypeResult[ScType]
  def viewBound: List[ScType] = Nil
  def contextBound: List[ScType] = Nil

  def upperTypeElement: Option[ScTypeElement] = None
  def lowerTypeElement: Option[ScTypeElement] = None
  def viewTypeElement: List[ScTypeElement] = Nil
  def contextBoundTypeElement: List[ScTypeElement] = Nil
}