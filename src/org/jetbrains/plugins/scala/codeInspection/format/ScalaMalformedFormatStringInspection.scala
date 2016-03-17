package org.jetbrains.plugins.scala
package codeInspection.format

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.codeInspection.{AbstractInspection, ProblemsHolderExt}
import org.jetbrains.plugins.scala.format.{Injection, Span, _}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem

/**
  * // Acceptance test

  * // Expression kind
  * "value: %d".format("123") // call format
  * "value: %d" format "123"  //  infix format
  * "123".formatted("%d") // call formatted
  * "123" formatted "%d" // infix formatted
  * String.format("%d", "123") // java call format
  * String format ("%d", "123") // java infix format
  * printf("%d", "123") // printf
  * System.out.printf("%d", "123") // System.out.printf

  * // Warning kind
  * "value: %d".format() // no argument
  * "value: %1$d".format() // no positional argument
  * "value: %d".format("123") // inconvertible type
  * "value: ".format(123) // unused argument

  * // Multi-line string
  * """value: %d""".format("123") // call format

  * // Specifier type
  * "value: %d".format(123) // call format
  * "value: %b".format(true) // call format
  * "value: %f".format(0.5F) // call format
  * "value: %c".format('c') // call format
  * "value: %s".format(123) // call format

  * // Interpolated strings ...
 */

/**
 * Pavel Fatin
 */

class ScalaMalformedFormatStringInspection extends AbstractInspection {
  def actionFor(holder: ProblemsHolder) = {
    case element =>
      val representation = FormattedStringParser.parse(element)
              .orElse(PrintStringParser.parse(element))
              .orElse(InterpolatedStringParser.parse(element))

      for (parts <- representation; part <- parts)
        inspect(part, holder)
  }

  private def inspect(part: StringPart, holder: ProblemsHolder)
                     (implicit typeSystem: TypeSystem = holder.typeSystem) {
    part match {
      case injection @ Injection(exp, Some(Specifier(Span(element, start, end), format))) =>
        injection.problem match {
          case Some(Inapplicable) =>
            for (argumentType <- injection.expressionType) {
              holder.registerProblem(element, new TextRange(start, end),
                "Format specifier %s cannot be used for an argument %s (%s)".format(
                  format, exp.getText, argumentType.presentableText))
              holder.registerProblem(exp,
                "Argument %s (%s) cannot be used for a format specifier %s".format(
                  exp.getText, argumentType.presentableText, format))
            }
          case Some(Malformed) =>
            holder.registerProblem(element, new TextRange(start, end), "Malformed format specifier")
          case _ =>
        }

      case UnboundSpecifier(Specifier(Span(element, start, end), format)) =>
        holder.registerProblem(element, new TextRange(start, end),
          "No argument for a format specifier %s".format(format))

      case UnboundPositionalSpecifier(Specifier(Span(element, start, end), format), position) =>
        holder.registerProblem(element, new TextRange(start, end),
          "No argument at position %d".format(position))

      case UnboundExpression(argument) =>
        holder.registerProblem(argument, "No format specifer for an argument %s".format(argument.getText),
          ProblemHighlightType.LIKE_UNUSED_SYMBOL)

      case _ =>
    }
  }
}
