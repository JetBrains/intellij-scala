package org.jetbrains.plugins.scala
package codeInspection.parentheses

import javax.swing.JComponent

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.intention.IntentionUtil
import org.jetbrains.plugins.scala.codeInspection.parentheses.UnnecessaryParenthesesUtil.{canBeStripped, canTypeBeStripped, getTextOfStripped}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection, InspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParenthesisedTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.getShortText
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker.checkInspection

import scala.annotation.tailrec

/**
 * Nikolay.Tropin
 * 4/25/13
 */
abstract class ScalaUnnecessaryParenthesesInspectionBase extends AbstractInspection("UnnecessaryParenthesesU", "Remove unnecessary parentheses") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case parenthesized: ScParenthesisedExpr
      if !parenthesized.getParent.isInstanceOf[ScParenthesisedExpr] &&
        checkInspection(this, parenthesized) &&
        canBeStripped(parenthesized, getIgnoreClarifying) =>

      holder.registerProblem(parenthesized, "Unnecessary parentheses", ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                             new UnnecessaryParenthesesQuickFix(parenthesized, getTextOfStripped(parenthesized, getIgnoreClarifying)))

    case parenthesizedType: ScParenthesisedTypeElement
      if !parenthesizedType.getParent.isInstanceOf[ScParenthesisedTypeElement] &&
        checkInspection(this, parenthesizedType) &&
        canTypeBeStripped(parenthesizedType, getIgnoreClarifying) =>

      holder.registerProblem(parenthesizedType, "Unnecessary parentheses", ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                             new UnnecessaryParenthesesAroundTypeQuickFix(parenthesizedType, getIgnoreClarifying))
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

/** Quickfix for unnecessary parentheses found on a TypeElement. */
class UnnecessaryParenthesesAroundTypeQuickFix(parenthesizedType: ScParenthesisedTypeElement, ignoreClarifying:Boolean)
  extends AbstractFixOnPsiElement("Remove unnecessary parentheses " + getShortText(parenthesizedType), parenthesizedType) {

  override protected def doApplyFix(element: ScParenthesisedTypeElement)(implicit project: Project): Unit = {
    val keepParentheses = parenthesizedType.typeElement.exists(_.isInstanceOf[ScParenthesisedTypeElement])
    val replaced = parenthesizedType.stripParentheses(keepParentheses) match {
        // Remove the last level of parentheses if allowed
        case paren: ScParenthesisedTypeElement if canTypeBeStripped(paren, ignoreClarifying) => paren.stripParentheses()
        case other => other
    }

    val comments = element.typeElement.map(expr => IntentionUtil.collectComments(expr))
    comments.foreach(IntentionUtil.addComments(_, replaced.getParent, replaced))

    ScalaPsiUtil.padWithWhitespaces(replaced)
  }
}

object UnnecessaryParenthesesUtil {

  def canBeStripped(parenthesized: ScParenthesisedExpr, ignoreClarifying: Boolean): Boolean
  = canBeStripped[ScParenthesisedExpr](ignoreClarifying, parenthesized,
                                       expressionParenthesesAreClarifying,
                                       p => ScalaPsiUtil.needParentheses(p, p.expr.get))


  private def expressionParenthesesAreClarifying(p: ScParenthesisedExpr)
  = (p.getParent, p.expr.get) match {
    case (_: ScSugarCallExpr, _: ScSugarCallExpr) => true
    case _ => false
  }

  def canTypeBeStripped(parenthesizedType: ScParenthesisedTypeElement, ignoreClarifying: Boolean): Boolean
  = canBeStripped[ScParenthesisedTypeElement](ignoreClarifying, parenthesizedType,
                                              typeParenthesesAreClarifying,
                                              ScTypeUtil.typeNeedsParentheses)


  private def typeParenthesesAreClarifying(p: ScParenthesisedTypeElement) = {
    import ScTypeUtil.getPrecedence

    (p.getParent, p.typeElement) match {
      case (parent: ScTypeElement, Some(child)) if getPrecedence(child) < ScTypeUtil.HighestPrecedence && getPrecedence(parent) != getPrecedence(child) => true
      case _ => false
    }
  }

  private def canBeStripped[T](ignoreClarifying: Boolean, parenthesized: T, isClarifying: T => Boolean, needsParen: T => Boolean): Boolean
  = (!ignoreClarifying || ignoreClarifying && !isClarifying(parenthesized)) && !needsParen(parenthesized)


  @tailrec
  def getTextOfStripped(expr: ScExpression, ignoreClarifying: Boolean): String = expr match {
    case parenthesized @ ScParenthesisedExpr(inner) if canBeStripped(parenthesized, ignoreClarifying) =>
      getTextOfStripped(inner, ignoreClarifying)
    case _ => expr.getText
  }
}

