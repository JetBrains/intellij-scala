package org.jetbrains.plugins.scala
package format

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * Pavel Fatin
 */

object StringConcatenationParser extends StringParser {
  override def parse(element: PsiElement): Option[Seq[StringPart]] = {
    Some(element) collect {
      case StringConcatenationExpression(left, right) =>
        val prefix = parse(left).getOrElse(parseOperand(left))
        prefix ++: parseOperand(right)
    }
  }

  private def parseOperand(exp: ScExpression): Seq[StringPart] = exp match {
    case WithStrippedMargin.StripMarginCall(_, lit, _) =>
      StripMarginParser.parse(lit).getOrElse(Nil)
    case interpolated: ScInterpolatedStringLiteral =>
      InterpolatedStringParser.parse(interpolated).getOrElse(Nil).toList
    case literal: ScLiteral =>
      val value = Option(literal.getValue).toSeq
      value.flatMap(v => Text(v.toString).withEscapedPercent(exp.getManager))
    case it =>
      FormattedStringParser.parse(it).map(_.toList).getOrElse(Injection(it, None) :: Nil)
  }
}
