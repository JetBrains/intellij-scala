package org.jetbrains.plugins.scala.codeInspection.format

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.format.Injection._
import org.jetbrains.plugins.scala.format._
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext

/**
 * NOTE!!!<br>
 * We currently don't handle interpolated string in formatted context.<br>
 * See comment inside [[org.jetbrains.plugins.scala.format.FormattedStringParser.parse]] and SCL-15414
 *
 * @todo add tests for f"" strings
 * @todo handle invalid specifiers (java does handle it) {{{
 *  """%""".format()
 *  """% """.format()
 *  """%  """.format()
 *  """text %""".format()
 *  """text % """.format()
 *  """text %! %""".format()
 *  """text %! %! %""".format()
 *  """text %) %""".format()
 *
 *  f"""%"""
 *  f"""% """
 *  f"""%  """
 *  f"""text %"""
 *  f"""text % """
 *  f"""text %! %"""
 *  f"""text %! %! %"""
 *  f"""text %) %"""
 *
 *  val a = 42f
 *  f"${a}%2.2%"
 * }}}
 */

class ScalaMalformedFormatStringInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    element =>
      val res0 = FormattedStringParser.parse(element)
      val res1 = res0.orElse(FormattedPrintStringParser.parse(element))
      val res2 = res1.orElse(InterpolatedStringParser.parse(element))
      val representation = res2

      representation.foreach(inspect(_, holder))
  }

  private def inspect(parts: Seq[StringPart], holder: ProblemsHolder): Unit =
    parts.foreach(inspect(_, holder))

  private def inspect(part: StringPart, holder: ProblemsHolder): Unit = {
    part match {
      case injection @ Injection(exp, Some(Specifier(Span(element, start, end), format))) =>
        implicit val tpc: TypePresentationContext = TypePresentationContext(element)
        injection.problem match {
          case Some(Inapplicable) =>
            for (argumentType <- injection.expressionType) {
              holder.registerProblem(element, new TextRange(start, end),
                ScalaInspectionBundle.message("format.specifier.cannot.be.used.for.an.argument", format, exp.getText, argumentType.presentableText))
              holder.registerProblem(exp,
                ScalaInspectionBundle.message("argument.cannot.be.used.for.a.format.specifier", exp.getText, argumentType.presentableText, format))
            }
          case Some(Malformed) =>
            /**
             * TODO: Show details of what is malformed, like in java SCL-18606
             * @see [[com.siyeh.ig.bugs.MalformedFormatStringInspection]]
             * @see [[com.siyeh.ig.bugs.FormatDecode]]
             */
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
        holder.registerProblem(argument, ScalaInspectionBundle.message("no.format.specifier.for.an.argument", argument.getText), ProblemHighlightType.LIKE_UNUSED_SYMBOL)

      case _ =>
    }
  }
}
