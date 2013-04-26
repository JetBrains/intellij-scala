package org.jetbrains.plugins.scala
package codeInspection.booleans

import org.jetbrains.plugins.scala.codeInspection.{AbstractFix, AbstractInspection}
import com.intellij.codeInspection.{ProblemDescriptor, ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScPrefixExpr, ScParenthesisedExpr, ScExpression}
import scala.annotation.tailrec
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import scala.collection.mutable


/**
 * Nikolay.Tropin
 * 4/23/13
 */

class DoubleNegationInspection extends AbstractInspection("DoubleNegation", "Double negation"){
  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case expr: ScExpression if (DoubleNegationUtil.hasDoubleNegation(expr)) =>
      holder.registerProblem(expr, "Double negation", ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new DoubleNegationQuickFix(expr))
    case _ =>
  }
}

class DoubleNegationQuickFix(expr: ScExpression) extends AbstractFix("Remove double negation", expr){
  def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!expr.isValid || !DoubleNegationUtil.hasDoubleNegation(expr)) return

    val newExpr = DoubleNegationUtil.removeDoubleNegation(expr)
    expr.replaceExpression(newExpr, removeParenthesis = true)
  }
}

object DoubleNegationUtil {

  def hasDoubleNegation(expr: ScExpression): Boolean = {
    if (hasNegation(expr))
      expr match {
        case prefix: ScPrefixExpr => hasNegation(prefix.operand)
        case infix: ScInfixExpr => hasNegation(infix.lOp) || hasNegation(infix.rOp)
        case _ => false
      }
    else
      expr match {
        case infix: ScInfixExpr => infix.operation.refName == "==" && hasNegation(infix.lOp) && hasNegation(infix.rOp)
        case _ => false
      }
  }

  def removeDoubleNegation(expr: ScExpression): ScExpression = {
    val text: String = stripParentheses(expr) match {
      case prefixExpr: ScPrefixExpr => invertedNegationText(prefixExpr.operand)
      case infixExpr: ScInfixExpr =>
        val left = infixExpr.lOp
        val right = infixExpr.rOp
        val hasNegLeft = hasNegation(left)
        val hasNegRight = hasNegation(right)
        val hasNegInfix = hasNegation(infixExpr)
        val builder = new mutable.StringBuilder()
        builder.append(if (hasNegLeft) invertedNegationText(left) else left.getText)
        builder.append(if (hasNegLeft && hasNegInfix && hasNegRight) " != " else " == ")
        builder.append(if (hasNegRight) invertedNegationText(right) else right.getText)
        builder.toString()
    }
    ScalaPsiElementFactory.createExpressionFromText(text, expr.getManager)
  }

  @tailrec
  private def stripParentheses(expr: ScExpression): ScExpression = expr match {
    case parenthesized: ScParenthesisedExpr => stripParentheses(parenthesized.expr.get)
    case expr: ScExpression => expr
  }

  private def hasNegation(expr: ScExpression): Boolean = {
    val withoutParentheses = stripParentheses(expr)
    withoutParentheses match {
      case prefix: ScPrefixExpr => prefix.operation.refName == "!"
      case infix: ScInfixExpr => infix.operation.refName == "!="
      case _ => false
    }
  }

   private def invertedNegationText(expr: ScExpression): String = {
    require(hasNegation(expr))
    val withoutParentheses = stripParentheses(expr)
    withoutParentheses match {
      case prefixExpr: ScPrefixExpr => prefixExpr.operand.getText
      case infixExpr: ScInfixExpr =>
        val lOpText = infixExpr.lOp.getText
        val rOpText = infixExpr.rOp.getText
        s"$lOpText == $rOpText"
    }
  }
}
