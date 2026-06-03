package org.jetbrains.plugins.scala.format

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

object StringConcatenationParser extends StringParser {
  override def parse(element: PsiElement): Option[Seq[StringPart]] = {
    val operands = detectOperands(element)
    operands.map { case (left, right) =>
      val leftParsed = parse(left)
      val prefix = leftParsed.getOrElse(parseOperand(left))
      prefix ++: parseOperand(right)
    }
  }

  def detectOperands(element: PsiElement): Option[(ScExpression, ScExpression)] =
    element match {
      case StringConcatenationExpression(left, right) => Some((left, right))
      case _ => None
    }

  private def parseOperand(exp: ScExpression): Seq[StringPart] = exp match {
    case WithStrippedMargin.StripMarginCall(_, lit, _) =>
      StripMarginParser.parse(lit).getOrElse(Nil)
    case string: ScStringLiteral =>
      ScStringLiteralParser.parse(string, checkStripMargin = true).getOrElse(Nil)
    case literal: ScLiteral =>
      val value = Option(literal.getValue).toSeq
      value.map(v => Text(v.toString))
    case it =>
      val formattedResult = FormattedStringParser.parse(it).map(_.toList)
      formattedResult.getOrElse(Injection(it, None) :: Nil)
  }
}
