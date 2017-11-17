package org.jetbrains.plugins.scala
package codeInspection.parentheses

import javax.swing.JComponent

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.intention.IntentionUtil
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection, InspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.getShortText
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

import scala.annotation.tailrec

/**
 * Nikolay.Tropin
 * 4/25/13
 */
abstract class ScalaUnnecessaryParenthesesInspectionBase extends AbstractInspection("UnnecessaryParenthesesU", "Remove unnecessary parentheses") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case parenthesized: ScParenthesisedExpr
      if !parenthesized.getParent.isInstanceOf[ScParenthesisedExpr] && IntentionAvailabilityChecker.checkInspection(this, parenthesized) &&
        UnnecessaryParenthesesUtil.canBeStripped(parenthesized, getIgnoreClarifying) =>
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
        extends AbstractFixOnPsiElement("Remove unnecessary parentheses " + getShortText(parenthesized), parenthesized){

  override protected def doApplyFix(parenthExpr: ScParenthesisedExpr)(implicit project: Project): Unit = {
    val newExpr = createExpressionFromText(textOfStripped)
    val replaced = parenthExpr.replaceExpression(newExpr, removeParenthesis = true)

    val comments = Option(parenthExpr.expr.get).map(expr => IntentionUtil.collectComments(expr))
    comments.foreach(value => IntentionUtil.addComments(value, replaced.getParent, replaced))

    ScalaPsiUtil.padWithWhitespaces(replaced)
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

