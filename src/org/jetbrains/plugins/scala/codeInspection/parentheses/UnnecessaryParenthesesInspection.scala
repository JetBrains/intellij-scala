package org.jetbrains.plugins.scala
package codeInspection.parentheses

import org.jetbrains.plugins.scala.codeInspection.{AbstractFix, AbstractInspection}
import com.intellij.codeInspection.{ProblemDescriptor, ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.openapi.project.Project
import scala.annotation.tailrec
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * Nikolay.Tropin
 * 4/25/13
 */
class UnnecessaryParenthesesInspection extends AbstractInspection("UnnecessaryParentheses", "Remove unnecessary parentheses") {

  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case parenthesized: ScParenthesisedExpr
      if !parenthesized.getParent.isInstanceOf[ScParenthesisedExpr] && UnnecessaryParenthesesUtil.canBeStripped(parenthesized) =>
      holder.registerProblem(parenthesized, "Unnecessary parentheses", ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        new UnnecessaryParenthesesQuickFix(parenthesized))
  }
}

class UnnecessaryParenthesesQuickFix(parenthesized: ScParenthesisedExpr)
        extends AbstractFix("Remove unnecessary parentheses " + parenthesized.getText, parenthesized){

  def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!parenthesized.isValid) return

    val stripped: String = UnnecessaryParenthesesUtil.getTextOfStripped(parenthesized)
    val newExpr = ScalaPsiElementFactory.createExpressionFromText(stripped, parenthesized.getManager)
    parenthesized.replaceExpression(newExpr, removeParenthesis = true)
  }
}

object UnnecessaryParenthesesUtil {

  def canBeStripped(parenthesized: ScParenthesisedExpr): Boolean = parenthesized match {
    case ScParenthesisedExpr(inner) => !ScalaPsiUtil.needParentheses(parenthesized, inner)
    case _ => false
  }

  @tailrec
  def getTextOfStripped(expr: ScExpression): String = expr match {
    case parenthesized @ ScParenthesisedExpr(inner) if canBeStripped(parenthesized) => getTextOfStripped(inner)
    case _ => expr.getText
  }
}

