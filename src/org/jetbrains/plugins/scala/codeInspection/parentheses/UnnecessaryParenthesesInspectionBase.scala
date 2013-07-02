package org.jetbrains.plugins.scala
package codeInspection.parentheses

import org.jetbrains.plugins.scala.codeInspection.{InspectionBundle, AbstractFix, AbstractInspection}
import com.intellij.codeInspection.{ProblemDescriptor, ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.getShortText
import javax.swing.JComponent
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel
import scala.annotation.tailrec

/**
 * Nikolay.Tropin
 * 4/25/13
 */
abstract class UnnecessaryParenthesesInspectionBase extends AbstractInspection("UnnecessaryParentheses", "Remove unnecessary parentheses") {

  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case parenthesized: ScParenthesisedExpr
      if !parenthesized.getParent.isInstanceOf[ScParenthesisedExpr] && UnnecessaryParenthesesUtil.canBeStripped(parenthesized, getIgnoreClarifying) =>
      holder.registerProblem(parenthesized, "Unnecessary parentheses", ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        new UnnecessaryParenthesesQuickFix(parenthesized, UnnecessaryParenthesesUtil.getTextOfStripped(parenthesized, getIgnoreClarifying)))
  }

  override def createOptionsPanel(): JComponent = {
    new SingleCheckboxOptionsPanel(InspectionBundle.message("ignore.clarifying.parentheses"), this, "ignoreClarifying")
  }

  def getIgnoreClarifying: Boolean
  def setIgnoreClarifying(value: Boolean)
}

class UnnecessaryParenthesesQuickFix(parenthesized: ScParenthesisedExpr, textOfStripped: String)
        extends AbstractFix("Remove unnecessary parentheses " + getShortText(parenthesized), parenthesized){

  def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!parenthesized.isValid) return

    val newExpr = ScalaPsiElementFactory.createExpressionFromText(textOfStripped, parenthesized.getManager)
    parenthesized.replaceExpression(newExpr, removeParenthesis = true)
  }
}

object UnnecessaryParenthesesUtil {

  @tailrec
  def canBeStripped(parenthesized: ScParenthesisedExpr, ignoreClarifying: Boolean): Boolean = {
      parenthesized match {
      case ScParenthesisedExpr(inner) if ignoreClarifying =>
        (parenthesized.getParent, inner) match {
          case (_: ScSugarCallExpr, _: ScSugarCallExpr) => false
          case _ => canBeStripped(parenthesized, ignoreClarifying = false)
        }
      case ScParenthesisedExpr(inner) => !ScalaPsiUtil.needParentheses(parenthesized, inner)
      case _ => false
    }

  }

  @tailrec
  def getTextOfStripped(expr: ScExpression, ignoreClarifying: Boolean): String = expr match {
    case parenthesized @ ScParenthesisedExpr(inner) if canBeStripped(parenthesized, ignoreClarifying) =>
      getTextOfStripped(inner, ignoreClarifying)
    case _ => expr.getText
  }
}

