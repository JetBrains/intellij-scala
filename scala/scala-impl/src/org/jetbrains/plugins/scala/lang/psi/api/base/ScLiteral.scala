package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaType, api}

trait ScLiteral extends ScExpression
  with PsiLiteral {

  protected type V >: Null <: AnyRef

  override def getValue: V

  def contentRange: TextRange

  def contentText: String

  /**
   * From https://docs.scala-lang.org/scala3/reference/syntax.html
   * {{{
   *  Singleton     ::=  SimpleRef
   *                   |  SimpleLiteral
   *                   |  Singleton ‘.’ id
   *
   *  SimpleLiteral ::=  [‘-’] integerLiteral
   *                   |  [‘-’] floatingPointLiteral
   *                   |  booleanLiteral
   *                   |  characterLiteral
   *                   |  stringLiteral
   *
   *  Literal       ::=  SimpleLiteral
   *                   |  processedStringLiteral
   *                   |  symbolLiteral
   *                   |  ‘null’
   * }}}
   */
  def isSimpleLiteral: Boolean
}

object ScLiteral {

  trait Numeric extends ScLiteral {

    override protected type V >: Null <: Number

    private[psi] type T <: AnyVal

    private[psi] def unwrappedValue(value: V): T

    override final def isSimpleLiteral: Boolean = true
  }

  private[psi] abstract class NumericCompanion[L <: Numeric] {

    final def unapply(literal: L): Option[L#T] = literal.getValue match {
      case null => None
      case value => Some(literal.unwrappedValue(value))
    }
  }

  private[psi] abstract class Value[V <: Any](val value: V) {

    def presentation: String = String.valueOf(value)

    def wideType(implicit project: Project): ScType
  }
}