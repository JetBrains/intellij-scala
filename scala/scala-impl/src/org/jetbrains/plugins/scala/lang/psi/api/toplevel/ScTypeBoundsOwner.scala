package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.ir.typeTree.TypeTreeHolder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScContextBound, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScType}

import scala.annotation.unused

trait ScTypeBoundsOwner extends ScNamedElement with ScTypeParametersOwner {
  def lowerBound(implicit context: Context): TypeResult
  def upperBound(implicit context: Context): TypeResult

  def viewBound: Seq[ScType] = Nil
  def contextBound: Seq[ScType] = Nil

  def hasBounds: Boolean = lowerTypeTreeHolder.nonEmpty || upperTypeTreeHolder.nonEmpty
  def hasImplicitBounds: Boolean = viewTypeTreeHolders.nonEmpty || contextBounds.nonEmpty

  def lowerTypeTreeHolder: Option[TypeTreeHolder] = None
  def upperTypeTreeHolder: Option[TypeTreeHolder] = None
  def viewTypeTreeHolders: Seq[TypeTreeHolder] = Nil

  def lowerTypePsiElement: Option[ScTypeElement] = None
  def upperTypePsiElement: Option[ScTypeElement] = None
  def viewTypePsiElements: Seq[ScTypeElement] = Nil
  def contextBounds: Seq[ScContextBound] = Nil

  def removeImplicitBounds(): Unit = {}

  @unused("debug utility")
  def boundsText: String = {
    ???
  }
}