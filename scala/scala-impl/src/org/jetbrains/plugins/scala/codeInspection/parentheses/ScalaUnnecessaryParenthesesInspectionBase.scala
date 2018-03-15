package org.jetbrains.plugins.scala
package codeInspection.parentheses

import javax.swing.JComponent

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.intention.IntentionUtil
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection, InspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScGenericParenthesisedNode
import org.jetbrains.plugins.scala.lang.psi.api.base.ScGenericParenthesisedNode.{AnyParenthesisedNode, Parenthesised}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.getShortText
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker.checkInspection

import scala.annotation.tailrec

/**
 * Nikolay.Tropin
 * 4/25/13
 */
abstract class ScalaUnnecessaryParenthesesInspectionBase extends AbstractInspection("UnnecessaryParenthesesU", "Remove unnecessary parentheses") {


  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case p: AnyParenthesisedNode if isProblem(p) => registerProblem(p)
  }

  override def createOptionsPanel(): JComponent = {
    new SingleCheckboxOptionsPanel(InspectionBundle.message("ignore.clarifying.parentheses"), this, "ignoreClarifying")
  }

  def getIgnoreClarifying: Boolean
  def setIgnoreClarifying(value: Boolean)

  private def isProblem[T <: ScalaPsiElement](elem: Parenthesised[T]): Boolean
  = !elem.isNestedParenthesis && checkInspection(this, elem) && elem.isParenthesisRedundant(getIgnoreClarifying)


  private def registerProblem[T <: ScalaPsiElement](elt: Parenthesised[T])(implicit holder: ProblemsHolder): Unit = {
    holder.registerProblem(elt, "Unnecessary parentheses", ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                           new UnnecessaryParenthesesTypeOrPatternQuickFix[T](elt, getIgnoreClarifying))
  }
}

class UnnecessaryParenthesesTypeOrPatternQuickFix[T <: ScalaPsiElement](parenthesized: Parenthesised[T], ignoreClarifying: Boolean)
  extends AbstractFixOnPsiElement("Remove unnecessary parentheses " + getShortText(parenthesized), parenthesized) {

  override protected def doApplyFix(element: Parenthesised[T])(implicit project: Project): Unit = {
    val keepParentheses = element.isNestingParenthesis
    // remove first the duplicate parentheses
    val replaced = element.stripParentheses(keepParentheses) match {
      // Remove the last level of parentheses if allowed
      case paren @ ScGenericParenthesisedNode(_) if paren.isParenthesisRedundant(ignoreClarifying) => paren.stripParentheses()
      case other => other
    }

    val comments = element.subNode.map(expr => IntentionUtil.collectComments(expr))
    comments.foreach(IntentionUtil.addComments(_, replaced.getParent, replaced))

    ScalaPsiUtil padWithWhitespaces replaced
  }
}

// should be deprecated, or deleted idk
object UnnecessaryParenthesesUtil {

  def canBeStripped(parenthesized: ScParenthesisedExpr, ignoreClarifying: Boolean): Boolean = parenthesized.isParenthesisNeeded(ignoreClarifying)

  @tailrec
  def getTextOfStripped(expr: ScExpression, ignoreClarifying: Boolean): String = expr match {
    case parenthesized @ ScParenthesisedExpr(inner) if canBeStripped(parenthesized, ignoreClarifying) =>
      getTextOfStripped(inner, ignoreClarifying)
    case _ => expr.getText
  }
}