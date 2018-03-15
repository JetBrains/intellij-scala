package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.openapi.util.TextRange
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiAnnotationOwner, PsiElement, PsiLanguageInjectionHost, PsiLiteral}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.02.2008
  */

trait ScLiteral extends ScExpression with PsiLiteral with PsiLanguageInjectionHost {
  /**
    * This method works only for null literal (to avoid possibly dangerous usage)
    *
    * @param tp type, which should be returned by method getTypeWithouImplicits
    */
  def setTypeForNullWithoutImplicits(tp: Option[ScType])

  def getTypeForNullWithoutImplicits: Option[ScType]

  def isString: Boolean

  def isMultiLineString: Boolean

  def getAnnotationOwner(annotationOwnerLookUp: ScLiteral => Option[PsiAnnotationOwner with PsiElement]): Option[PsiAnnotationOwner]

  def isSymbol: Boolean

  def isChar: Boolean

  def contentRange: TextRange

  def allowLiteralTypes: Boolean
}

object ScLiteral {
  def unapply(literal: ScLiteral) = Some(literal.getValue)
}

sealed abstract class ScLiteralValueExtractor[T](literalTypes: IElementType*) {

  private val types = literalTypes.toSet

  protected def cast(value: AnyRef): T

  def unapply(literal: ScLiteral): Option[T] = {
    val elementType = literal.getFirstChild.getNode.getElementType
    val value = if (isAvailableFor(literal) && types(elementType)) Option(literal.getValue)
    else None
    value.map(cast)
  }

  protected def isAvailableFor(literal: ScLiteral): Boolean = true
}

object ScIntLiteral extends ScLiteralValueExtractor[Int](tINTEGER) {

  override protected def cast(value: AnyRef): Int = value match {
    case integer: java.lang.Integer => integer.intValue()
  }

  override protected def isAvailableFor(literal: ScLiteral): Boolean =
    literal.getText.last.isDigit
}

object ScBooleanLiteral extends ScLiteralValueExtractor[Boolean](kTRUE, kFALSE) {

  override protected def cast(value: AnyRef): Boolean = value match {
    case boolean: java.lang.Boolean => boolean.booleanValue()
  }
}

object ScStringLiteral extends ScLiteralValueExtractor[String](tSTRING, tWRONG_STRING, tMULTILINE_STRING) {

  override protected def cast(value: AnyRef): String = value match {
    case string: String => string
  }
}