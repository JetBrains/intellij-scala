package org.jetbrains.plugins.scala
package format

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.ScalaType.expandAliases
import org.jetbrains.plugins.scala.project.ProjectContext

import java.util.{IllegalFormatConversionException, IllegalFormatException}

sealed trait StringPart

case class Text(value: String) extends StringPart

/**
 * https://docs.oracle.com/javase/8/docs/api/java/util/Formatter.html#syntax
 *
 * Represents special escapes %% and %n which comes from formatted string.
 * Example: {{{
 *   f"aaa %% %n"
 *   "aaa %% %n".format()
 * }}}
 *
 * We can't hold such content as a simple Text("%n") or Text("\n").<br>
 * We need to preserve information whether content was originally obtained from formatted string.<br>
 * When we convert `f"%% %n"` to `String.format(...)` we want to get `String.format("%% %n")`<br>
 * When we convert it to string concatenation, we want it to become just "%"
 *
 * @param originalText  text from formatted string itself: "abc %% %n"
 */
case class SpecialFormatEscape(originalText: String, unescapedText: String) extends StringPart

object SpecialFormatEscape {
  val PercentChar: SpecialFormatEscape = SpecialFormatEscape("%%", "%")

  /**
   * Represents system line separator, taken from System.getProperty("line.separator"))
   * NOTE: we don't use system separator in unescaped text for simplicity, to avoid system-dependant behaviour in tests
   * TODO: should we using system property?
   *
   * @see https://docs.oracle.com/javase/8/docs/api/java/util/Formatter.html#syntax
   * @see [[scala.tools.reflect.FormatInterpolator.interpolated]]
   */
  val LineSeparator: SpecialFormatEscape = SpecialFormatEscape("%n", "\n")
}

case class Injection(expression: ScExpression, specifier: Option[Specifier]) extends StringPart {
  import Injection._

  private implicit def ctx: ProjectContext = expression

  def text: String = expression.getText

  def value: String = expression match {
    case literal: ScLiteral => literal.getValue.toString // 42 -> 42, "str" -> str
    case block: ScBlockExpr => block.exprs.headOption.map(_.getText).mkString
    case element            => element.getText
  }

  def format: String = specifier.map(_.format).getOrElse("")

  def expressionType: Option[ScType] = expression.`type`().map(_.widen).toOption

  def isLiteral: Boolean = expression.is[ScLiteral]

  def isAlphanumericIdentifier: Boolean = !isLiteral && expression.getText.forall(it => it.isLetter || it.isDigit)

  def isFormattingRequired: Boolean = specifier.exists(_.format.length > 2)

  def isComplexBlock: Boolean = expression match {
    case block: ScBlockExpr => block.exprs.length > 1
    case _ => false
  }

  def problem: Option[InjectionProblem] = specifier.flatMap { it =>
    expressionType.flatMap(expandAliases(_).toOption) match {
      case Some(result) =>
        try {
          val value = Types.valueOf(result)
          it.format.format(value)
          None
        } catch {
          case _: IllegalFormatConversionException => Some(Inapplicable)
          case _: IllegalFormatException => Some(Malformed)
        }
        case _ => Some(Malformed)
      }
  }
}

object Injection {
  sealed trait InjectionProblem
  case object Inapplicable extends InjectionProblem
  case object Malformed extends InjectionProblem
}

case class UnboundSpecifier(specifier: Specifier) extends StringPart

case class UnboundPositionalSpecifier(specifier: Specifier, position: Int) extends StringPart

case class UnboundExpression(expression: ScExpression) extends StringPart

case class Specifier(span: Span, format: String)

case class Span(element: PsiElement, start: Int, end: Int)