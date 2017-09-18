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
* Date: 22.02.2008
*/

trait ScLiteral extends ScExpression with PsiLiteral with PsiLanguageInjectionHost {
  /**
   * This method works only for null literal (to avoid possibly dangerous usage)
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
}

object ScLiteral {
  def unapply(literal: ScLiteral) = Some(literal.getValue)
}

sealed class ScLiteralValueExtractor[T](literalTypes: IElementType*)(cast: AnyRef => T) {
  private val types = literalTypes.toSet

  def unapply(literal: ScLiteral): Option[T] = {
    val node = literal.getFirstChild.getNode
    val literalType = node.getElementType
    if (types.contains(literalType) && isAvailableFor(literal))
      Some(cast(literal.getValue))
    else None
  }

  def isAvailableFor(literal: ScLiteral): Boolean = true
}

object ScLongLiteral extends ScLiteralValueExtractor(tINTEGER)(_.asInstanceOf[java.lang.Long].longValue) {
  override def isAvailableFor(literal: ScLiteral): Boolean = {
    val text = literal.getText
    text.endsWith("L") || text.endsWith("l")
  }
}

object ScIntLiteral extends ScLiteralValueExtractor(tINTEGER)(_.asInstanceOf[java.lang.Integer].intValue) {
  override def isAvailableFor(literal: ScLiteral): Boolean = literal.getText.forall(_.isDigit)
}

object ScFloatLiteral extends ScLiteralValueExtractor(tFLOAT)(_.asInstanceOf[java.lang.Float].floatValue)

object ScCharLiteral extends ScLiteralValueExtractor(tCHAR)(_.asInstanceOf[java.lang.Character].charValue)

object ScBooleanLiteral extends ScLiteralValueExtractor(kTRUE, kFALSE)(_.asInstanceOf[java.lang.Boolean].booleanValue)

object ScStringLiteral extends ScLiteralValueExtractor(tSTRING, tWRONG_STRING, tMULTILINE_STRING)(_.asInstanceOf[String])