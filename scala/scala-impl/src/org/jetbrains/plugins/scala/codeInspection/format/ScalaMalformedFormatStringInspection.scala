package org.jetbrains.plugins.scala
package codeInspection.format

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.format.Injection._
import org.jetbrains.plugins.scala.format.{Injection, Span, _}
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext

import scala.annotation.nowarn

/**
 * NOTE!!!<br>
 * We currently don't handle interpolated string in formatted context.
 * See comment inside [[org.jetbrains.plugins.scala.format.FormattedStringParser.parse]] and SCL-15414
 */
@nowarn("msg=" + AbstractInspection.DeprecationText)
class ScalaMalformedFormatStringInspection extends AbstractInspection {

  override def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Unit] = {
    case element =>
      val res0 = FormattedStringParser.parse(element)
      val res1 = res0.orElse(PrintStringParser.parse(element))
      val res2 = res1.orElse(InterpolatedStringParser.parse(element))
      val representation = res2

      for (parts <- representation; part <- parts)
        inspect(part, holder)
  }

  private def inspect(part: StringPart, holder: ProblemsHolder): Unit = {
    part match {
      case injection @ Injection(exp, Some(Specifier(Span(element, start, end), format))) =>
        implicit val tpc: TypePresentationContext = TypePresentationContext(element)
        injection.problem match {
          case Some(Inapplicable) =>
            for (argumentType <- injection.expressionType.map(_.widenIfLiteral)) {
              holder.registerProblem(element, new TextRange(start, end),
                ScalaInspectionBundle.message("format.specifier.cannot.be.used.for.an.argument", format, exp.getText, argumentType.presentableText))
              holder.registerProblem(exp,
                ScalaInspectionBundle.message("argument.cannot.be.used.for.a.format.specifier", exp.getText, argumentType.presentableText, format))
            }
          case Some(Malformed) =>
            holder.registerProblem(element, new TextRange(start, end), ScalaInspectionBundle.message("malformed.format.specifier"))
          case _ =>
        }

      case UnboundSpecifier(Specifier(Span(element, start, end), format)) =>
        holder.registerProblem(element, new TextRange(start, end),
          ScalaInspectionBundle.message("no.argument.for.a.format.specifier", format))

      case UnboundPositionalSpecifier(Specifier(Span(element, start, end), _), position) =>
        holder.registerProblem(element, new TextRange(start, end),
          ScalaInspectionBundle.message("no.argument.at.position", position.toString))

      case UnboundExpression(argument) =>
        holder.registerProblem(argument, ScalaInspectionBundle.message("no.format.specifer.for.an.argument", argument.getText),
          ProblemHighlightType.LIKE_UNUSED_SYMBOL)

      case _ =>
    }
  }
}
