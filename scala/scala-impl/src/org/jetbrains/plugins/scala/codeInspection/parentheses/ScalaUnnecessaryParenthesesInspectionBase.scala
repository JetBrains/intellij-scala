package org.jetbrains.plugins.scala
package codeInspection.parentheses

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel
import com.intellij.codeInspection.{LocalQuickFix, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import javax.swing.JComponent
import org.jetbrains.plugins.scala.codeInsight.intention.IntentionUtil
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection, InspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScParenthesizedElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, _}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.getShortText
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker.checkInspection

import scala.annotation.tailrec

/**
 * Nikolay.Tropin
 * 4/25/13
 */
abstract class ScalaUnnecessaryParenthesesInspectionBase extends AbstractInspection("UnnecessaryParenthesesU", "Remove unnecessary parentheses") {


  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case p: ScParenthesizedElement if isProblem(p) => registerProblem(p)
    // In the case of a single untyped formal parameter, (x) => e can be abbreviated to x => e
    case f @ ScFunctionExpr(Seq(param), _) if param.paramType.isEmpty && isParenthesised(f.params.clauses.head) => registerProblem(f.params.clauses.head)
    // If an anonymous function (x: T) => e with a single typed parameter appears as the result expression of a block, it can be abbreviated to x: T => e.
    case BlockResultExpr(f @ ScFunctionExpr(Seq(_), _)) if isParenthesised(f.params.clauses.head) => registerProblem(f.params.clauses.head)
  }


  override def createOptionsPanel(): JComponent = {
    new SingleCheckboxOptionsPanel(InspectionBundle.message("ignore.clarifying.parentheses"), this, "ignoreClarifying")
  }

  def getIgnoreClarifying: Boolean
  def setIgnoreClarifying(value: Boolean)

  private object BlockResultExpr {
    def unapply(arg: ScBlockExpr): Option[ScExpression] = arg.lastExpr
  }

  private def isParenthesised(clause: ScParameterClause): Boolean =
    clause.getNode.getFirstChildNode.getText == "(" && clause.getNode.getLastChildNode.getText == ")"

  private def isProblem(elem: ScParenthesizedElement): Boolean =
    !elem.isNestedParenthesis && checkInspection(this, elem) && elem.isParenthesisRedundant(getIgnoreClarifying)


  private def registerProblem(elt: ScParenthesizedElement)(implicit holder: ProblemsHolder): Unit =
    registerProblem(elt, new UnnecessaryParenthesesTypeOrPatternQuickFix(elt, getIgnoreClarifying))


  private def registerProblem(elt: ScParameterClause)(implicit holder: ProblemsHolder): Unit = {
    val quickFix = new AbstractFixOnPsiElement[ScParameterClause]("Remove unnecessary parentheses " + getShortText(elt), elt) {
      override protected def doApplyFix(element: ScParameterClause)(implicit project: Project): Unit = {
        if (isParenthesised(element)) {
          elt.getNode.removeChild(elt.getNode.getFirstChildNode)
          elt.getNode.removeChild(elt.getNode.getLastChildNode)
        }
      }
    }

    registerProblem(elt, quickFix)
  }


  private def registerProblem(elt: ScalaPsiElement, qf: LocalQuickFix)(implicit holder: ProblemsHolder): Unit =
    holder.registerProblem(elt, "Unnecessary parentheses", ProblemHighlightType.GENERIC_ERROR_OR_WARNING, qf)

}

class UnnecessaryParenthesesTypeOrPatternQuickFix(parenthesized: ScParenthesizedElement, ignoreClarifying: Boolean)
  extends AbstractFixOnPsiElement("Remove unnecessary parentheses " + getShortText(parenthesized), parenthesized) {

  override protected def doApplyFix(element: ScParenthesizedElement)(implicit project: Project): Unit = {
    val keepParentheses = element.isNestingParenthesis
    // remove first the duplicate parentheses
    val replaced = element.stripParentheses(keepParentheses) match {
      // Remove the last level of parentheses if allowed
      case paren: ScParenthesizedElement if paren.isParenthesisRedundant(ignoreClarifying) => paren.stripParentheses()
      case other => other
    }

    val comments = element.innerElement.map(expr => IntentionUtil.collectComments(expr))
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