package org.jetbrains.plugins.scala
package codeInspection.booleans

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScParenthesisedExpr, ScPrefixExpr}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

import scala.annotation.tailrec
import scala.collection.mutable


/**
 * Nikolay.Tropin
 * 4/23/13
 */
class DoubleNegationInspection extends AbstractInspection("DoubleNegationScala", "Double negation") {

  override protected def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case expr: ScExpression if DoubleNegationUtil.hasDoubleNegation(expr) =>
      holder.registerProblem(expr, "Double negation", ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new DoubleNegationQuickFix(expr))
    case _ =>
  }
}

class DoubleNegationQuickFix(expr: ScExpression)
  extends AbstractFixOnPsiElement("Remove double negation", expr) {

  override protected def doApplyFix(scExpr: ScExpression)
                                   (implicit project: Project): Unit = {
    if (!DoubleNegationUtil.hasDoubleNegation(scExpr)) return

    val newExpr = DoubleNegationUtil.removeDoubleNegation(scExpr)
    scExpr.replaceExpression(newExpr, removeParenthesis = true)
  }
}

object DoubleNegationUtil {

  def hasDoubleNegation(expr: ScExpression): Boolean = {
    if (hasNegation(expr))
      expr match {
        case ScPrefixExpr(_, operand) => hasNegation(operand)
        case ScInfixExpr(left, _, right) => hasNegation(left) || hasNegation(right)
        case _ => false
      }
    else
      expr match {
        case ScInfixExpr(left, operation, right) => operation.refName == "==" && hasNegation(left) && hasNegation(right)
        case _ => false
      }
  }

  def removeDoubleNegation(expr: ScExpression): ScExpression = {
    val text: String = stripParentheses(expr) match {
      case ScPrefixExpr(_, operand) => invertedNegationText(operand)
      case infix @ ScInfixExpr(left, _, right) =>
        val hasNegLeft = hasNegation(left)
        val hasNegRight = hasNegation(right)
        val hasNegInfix = hasNegation(infix)
        val builder = new mutable.StringBuilder()
        builder.append(if (hasNegLeft) invertedNegationText(left) else left.getText)
        builder.append(if (hasNegLeft && hasNegInfix && hasNegRight) " != " else " == ")
        builder.append(if (hasNegRight) invertedNegationText(right) else right.getText)
        builder.toString()
    }
    createExpressionFromText(text)(expr.getManager)
  }

  @tailrec
  private def stripParentheses(expr: ScExpression): ScExpression = expr match {
    case ScParenthesisedExpr(inner) => stripParentheses(inner)
    case expr: ScExpression => expr
  }

  private def hasNegation(expr: ScExpression): Boolean = {
    val withoutParentheses = stripParentheses(expr)
    withoutParentheses match {
      case ScPrefixExpr(operation, _) => operation.refName == "!"
      case ScInfixExpr(_, operation, _) => operation.refName == "!="
      case _ => false
    }
  }

   private def invertedNegationText(expr: ScExpression): String = {
    require(hasNegation(expr))
    val withoutParentheses = stripParentheses(expr)
    withoutParentheses match {
      case ScPrefixExpr(_, operand) => operand.getText
      case ScInfixExpr(left, _, right) => left.getText + "==" + right.getText
    }
  }
}
