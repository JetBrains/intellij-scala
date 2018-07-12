package org.jetbrains.plugins.scala
package codeInspection.parentheses

import com.intellij.codeInspection.{LocalQuickFix, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.codeInsight.intention.IntentionUtil
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.extensions.ParenthesizedElement.Ops
import org.jetbrains.plugins.scala.lang.psi.api.base.ScParenthesizedElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScFunctionalTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, _}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.getShortText
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker.checkInspection

/**
 * Nikolay.Tropin
 * 4/25/13
 */
abstract class ScalaUnnecessaryParenthesesInspectionBase
  extends AbstractInspection("ScalaUnnecessaryParentheses", "Remove unnecessary parentheses") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case p: ScParenthesizedElement if isProblem(p)                                                             => registerProblem(p)
    case f: ScFunctionExpr if hasSingleParenthesizedParam(f) && !currentSettings.ignoreAroundFunctionExprParam =>
      f.params.clauses.headOption.foreach(registerProblem)
  }

  def currentSettings: UnnecessaryParenthesesSettings

  @TestOnly
  def setSettings(settings: UnnecessaryParenthesesSettings): Unit

  private def hasSingleParenthesizedParam(f: ScFunctionExpr): Boolean = {

    // If an anonymous function (x: T) => e with a single typed parameter appears as the result expression of a block, it can be abbreviated to x: T => e.
    def isBlockResultExpr: Boolean = f.getParent match {
      case block: ScBlockExpr if block.lastExpr.contains(f) => true
      case _ => false
    }
    // In the case of a single untyped formal parameter, (x) => e can be abbreviated to x => e
    def hasNoParamType = f.parameters.headOption.exists(_.typeElement.isEmpty)

    def hasParenthesizedClause = f.params.clauses.headOption.exists(isParenthesised)

    f.parameters.size == 1 && hasParenthesizedClause && (hasNoParamType || isBlockResultExpr)
  }

  def isParenthesesRedundant(elem: ScParenthesizedElement, settings: UnnecessaryParenthesesSettings): Boolean = {
    if (elem.isParenthesisNeeded) return false

    elem match {
      case _ if elem.isParenthesisClarifying && settings.ignoreClarifying                          => false
      case ScParenthesizedElement(_: ScFunctionalTypeElement) if settings.ignoreAroundFunctionType => false
      case _ if elem.isFunctionTypeSingleParam && settings.ignoreAroundFunctionTypeParam           => false
      case _                                                                                       => true
    }
  }

  private def isParenthesised(clause: ScParameterClause): Boolean =
    clause.getNode.getFirstChildNode.getText == "(" && clause.getNode.getLastChildNode.getText == ")"

  private def isProblem(elem: ScParenthesizedElement): Boolean =
    !elem.isNestedParenthesis &&
      checkInspection(this, elem) &&
      isParenthesesRedundant(elem, currentSettings)


  private def registerProblem(parenthesized: ScParenthesizedElement)(implicit holder: ProblemsHolder): Unit =
    registerProblem(parenthesized, new AbstractFixOnPsiElement("Remove unnecessary parentheses " + getShortText(parenthesized), parenthesized) {
      override protected def doApplyFix(element: ScParenthesizedElement)(implicit project: Project): Unit = {
        val keepParentheses = element.isNestingParenthesis
        // remove first the duplicate parentheses
        val replaced = element.doStripParentheses(keepParentheses) match {
          // Remove the last level of parentheses if allowed
          case paren: ScParenthesizedElement if element.isParenthesisRedundant => paren.doStripParentheses()
          case other => other
        }

        val comments = element.innerElement.map(expr => IntentionUtil.collectComments(expr))
        comments.foreach(IntentionUtil.addComments(_, replaced.getParent, replaced))

        ScalaPsiUtil padWithWhitespaces replaced
      }
  })

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