package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiAnnotationOwner, PsiElement, PsiLanguageInjectionHost, PsiLiteral}
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
}

object ScLiteral {

  import lang.lexer.{ScalaTokenTypes => T}

  def unapply(literal: ScLiteral): Option[Value[_]] = Option {
    (literal.getValue, literal.getFirstChild.getNode.getElementType) match {
      case (integer: Integer, T.tINTEGER) if literal.getText.last.isDigit => IntegerValue(integer.intValue)
      case (boolean: java.lang.Boolean, T.kTRUE |
                                        T.kFALSE) => BooleanValue(boolean.booleanValue)
      case (character: Character, T.tCHAR) => CharacterValue(character.charValue)
      case (string: String, T.tSTRING |
                            T.tWRONG_STRING |
                            T.tMULTILINE_STRING) => StringValue(string)
      case (symbol: Symbol, T.tSYMBOL) => SymbolValue(symbol)
      case _ => null
    }
  }


  sealed abstract class Value[V <: Any](val value: V)

  final case class IntegerValue(override val value: Int) extends Value(value)

  final case class BooleanValue(override val value: Boolean) extends Value(value)

  final case class CharacterValue(override val value: Char) extends Value(value)

  final case class StringValue(override val value: String) extends Value(value)

  final case class SymbolValue(override val value: Symbol) extends Value(value)
}