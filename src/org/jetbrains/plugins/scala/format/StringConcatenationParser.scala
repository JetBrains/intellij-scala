package org.jetbrains.plugins.scala
package format

import com.intellij.psi.PsiElement
import lang.psi.api.base.ScLiteral
import lang.psi.api.expr.{ScExpression, ScInfixExpr}
import lang.psi.types.result.TypingContext
import lang.psi.types.ScDesignatorType
import extensions._

/**
 * Pavel Fatin
 */

object StringConcatenationParser extends StringParser {
  def parse(element: PsiElement): Option[List[StringPart]] = {
    Some(element) collect {
      case exp @ ScInfixExpr(left, op, right) if op.getText == "+" && isString(exp) =>
        val prefix = parse(left).getOrElse(parseOperand(left))
        prefix ::: parseOperand(right)
    }
  }

  private def parseOperand(exp: ScExpression): List[StringPart] = exp match {
    case literal: ScLiteral => Text(literal.getValue.toString) :: Nil
    case it => FormattedStringParser.parse(it).map(_.toList).getOrElse(Injection(it, None) :: Nil)
  }

  private def isString(exp: ScExpression) = exp.getType(TypingContext.empty).toOption match {
    case Some(ScDesignatorType(element)) => element.name == "String"
    case _ => false
  }
}
