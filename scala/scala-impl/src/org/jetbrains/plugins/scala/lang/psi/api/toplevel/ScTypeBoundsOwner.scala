package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScContextBound, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

import scala.annotation.unused

trait ScTypeBoundsOwner extends ScNamedElement with ScTypeParametersOwner {
  def lowerBound: TypeResult
  def upperBound: TypeResult

  def viewBound: Seq[ScType] = Nil
  def contextBound: Seq[ScType] = Nil

  def hasBounds: Boolean = lowerTypeElement.nonEmpty || upperTypeElement.nonEmpty
  def hasImplicitBounds: Boolean = viewTypeElement.nonEmpty || contextBounds.nonEmpty

  def lowerTypeElement: Option[ScTypeElement] = None
  def upperTypeElement: Option[ScTypeElement] = None

  def viewTypeElement: Seq[ScTypeElement] = Nil
  def contextBounds: Seq[ScContextBound] = Nil

  def removeImplicitBounds(): Unit = {}

  @unused("debug utility")
  def boundsText: String = {
    def toString(bounds: Iterable[ScTypeElement], elementType: IElementType) =
      bounds.map(e => s"${elementType.toString} ${e.getText}")

    def contextBoundsString: Seq[String] =
      contextBounds.map(bound => s": ${bound.typeElement.getText}")

    (toString(lowerTypeElement, tLOWER_BOUND) ++
      toString(upperTypeElement, tUPPER_BOUND) ++
      toString(viewTypeElement, tVIEW) ++
      contextBoundsString)
      .mkString(" ")
  }
}