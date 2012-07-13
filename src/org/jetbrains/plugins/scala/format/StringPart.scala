package org.jetbrains.plugins.scala
package format

import lang.psi.api.expr.{ScBlockExpr, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.types
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import types.result.TypingContext
import com.intellij.psi.PsiElement
import types.ScType
import java.util.{IllegalFormatConversionException, IllegalFormatException}

/**
 * Pavel Fatin
 */

sealed trait StringPart

case class Text(s: String) extends StringPart

case class Injection(expression: ScExpression, specifier: Option[Specifier]) extends StringPart {
  def text = expression.getText

  def value = expression match {
    case literal: ScLiteral => literal.getValue.toString
    case block: ScBlockExpr => block.exprs.headOption.map(_.getText).mkString
    case element => element.getText
  }

  def format = specifier.map(_.format).getOrElse("")

  def expressionType: Option[ScType] = expression.getType(TypingContext.empty).toOption

  def isLiteral = expression.isInstanceOf[ScLiteral]

  def isAlphanumericIdentifier = !isLiteral && expression.getText.forall(it => it.isLetter || it.isDigit)

  def isFormattingRequired = specifier.exists(_.format.length > 2)

  def isComplexBlock = expression match {
    case block: ScBlockExpr => block.exprs.length > 1
    case _ => false
  }

  def problem: Option[InjectionProblem] = specifier.flatMap { it =>
    val v = expressionType.map(Types.valueOf).getOrElse(new Object())
    try {
      v.formatted(it.format)
      None
    } catch {
      case e: IllegalFormatConversionException => Some(Inapplicable)
      case e: IllegalFormatException => Some(Malformed)
    }
  }
}

case class UnboundSpecifier(specifier: Specifier) extends StringPart

case class UnboundPositionalSpecifier(specifier: Specifier, position: Int) extends StringPart

case class UnboundExpression(expression: ScExpression) extends StringPart

case class Specifier(span: Span, format: String)

case class Span(element: PsiElement, start: Int, end: Int)

sealed trait InjectionProblem

case object Inapplicable extends InjectionProblem

case object Malformed extends InjectionProblem
