package org.jetbrains.plugins.scala.lang.psi.util

import com.intellij.psi.impl.ConstantExpressionEvaluator
import com.intellij.psi.PsiConstantEvaluationHelper.AuxEvaluator
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScParenthesisedExpr, ScExpression}

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaConstantExpressionEvaluator extends ConstantExpressionEvaluator {
  def computeExpression(expression: PsiElement, throwExceptionOnOverflow: Boolean,
                        auxEvaluator: AuxEvaluator): AnyRef = {
    expression match {
      case expr: ScExpression => evaluate(expr)
      case _ => null
    }
  }

  def computeConstantExpression(expression: PsiElement, throwExceptionOnOverflow: Boolean): AnyRef = {
    expression match {
      case expr: ScExpression => evaluate(expr)
      case _ => null
    }
  }

  private def evaluate(expr: ScExpression): AnyRef = {
    expr match {
      case l: ScLiteral => l.getValue
      case p: ScParenthesisedExpr => p.expr match {
        case Some(e) => evaluate(e)
        case _ => null
      }
      case _ => null
    }
  }
}