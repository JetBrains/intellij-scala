package org.jetbrains.plugins.scala
package format

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}

/**
 * Pavel Fatin
 */

object StringConcatenationParser extends StringParser {
  def parse(element: PsiElement): Option[Seq[StringPart]] = {
    Some(element) collect {
      case exp@ScInfixExpr(left, op, right) if op.getText == "+" && isString(exp) =>
        val prefix = parse(left).getOrElse(parseOperand(left))
        prefix ++: parseOperand(right)
    }
  }

  private def parseOperand(exp: ScExpression): Seq[StringPart] = {
    exp match {
      case IsStripMargin(lit, _) => return StripMarginParser.parse(lit).getOrElse(Nil)
      case _ =>
    }
    exp match {
      case interpolated: ScInterpolatedStringLiteral =>
        InterpolatedStringParser.parse(interpolated).getOrElse(Nil).toList
      case literal: ScLiteral =>
        val value = Option(literal.getValue).toSeq
        value.flatMap(v => Text(v.toString).withEscapedPercent(exp.getManager))
      case it => FormattedStringParser.parse(it).map(_.toList).getOrElse(Injection(it, None) :: Nil)
    }
  }

  def isString(exp: ScExpression): Boolean = exp.`type`().toOption match {
    case Some(ScDesignatorType(element)) => element.name == "String"
    case Some(ScProjectionType(ScDesignatorType(predef), ta: ScTypeAlias))  => predef.name == "Predef" && ta.name == "String"
    case _ => false
  }
}
