package org.jetbrains.plugins.scala.codeInspection.parentheses

import com.intellij.codeInspection.{LocalInspectionTool, LocalQuickFix, ProblemsHolder}
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.codeInsight.intention.RemoveBracesIntention
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.ParenthesizedElement.Ops
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScParenthesizedElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScFunctionalTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.getShortText
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker.checkInspection

abstract class ScalaUnnecessaryParenthesesInspectionBase extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case p: ScParenthesizedElement if isProblem(p) =>
      registerProblem(p, holder, isOnTheFly)
    case f: ScFunctionExpr if hasSingleParenthesizedParam(f) && !currentSettings.ignoreAroundFunctionExprParam =>
      f.params.clauses.headOption.foreach(clause => registerProblem(clause, holder, isOnTheFly))
    case _ =>
  }

  def currentSettings: UnnecessaryParenthesesSettings

  @TestOnly
  def setSettings(settings: UnnecessaryParenthesesSettings): Unit

  private def hasSingleParenthesizedParam(f: ScFunctionExpr): Boolean = {
    // In the case of a single untyped formal parameter, (x) => e can be abbreviated to x => e
    def hasNoParamType = f.parameters.headOption.exists(_.typeElement.isEmpty)

    def hasParenthesizedClause = f.params.clauses.headOption.exists(isParenthesised)

    f.parameters.size == 1 && hasParenthesizedClause && hasNoParamType
  }

  protected[scala] def isParenthesesRedundant(elem: ScParenthesizedElement): Boolean =
    isParenthesesRedundant(elem, currentSettings)

  private def isParenthesesRedundant(elem: ScParenthesizedElement, settings: UnnecessaryParenthesesSettings): Boolean = {
    if (elem.isParenthesisNeeded) return false

    elem match {
      case _ if elem.isParenthesisClarifying && settings.ignoreClarifying => false
      case ScParenthesizedElement(_: ScFunctionalTypeElement) if settings.ignoreAroundFunctionType => false
      case _ if elem.isFunctionTypeSingleParam && settings.ignoreAroundFunctionTypeParam => false
      case _ => true
    }
  }

  private def isParenthesised(clause: ScParameterClause): Boolean =
    clause.getNode.getFirstChildNode.getText == "(" && clause.getNode.getLastChildNode.getText == ")"

  private def isProblem(elem: ScParenthesizedElement): Boolean =
    !elem.isNestedParenthesis &&
      checkInspection(this, elem) &&
      isParenthesesRedundant(elem)


  private def registerProblem(parenthesized: ScParenthesizedElement, holder: ProblemsHolder, isOnTheFly: Boolean): Unit = {
    val description = ScalaInspectionBundle.message("remove.unnecessary.parentheses.with.text", getShortText(parenthesized))
    val foo = new AbstractFixOnPsiElement(description, parenthesized) {
      override protected def doApplyFix(element: ScParenthesizedElement)(implicit project: Project): Unit = {
        val keepParentheses = element.isNestingParenthesis
        // remove first the duplicate parentheses
        val replaced = element.doStripParentheses(keepParentheses) match {
          // Remove the last level of parentheses if allowed
          case paren: ScParenthesizedElement if paren.isParenthesisRedundant => paren.doStripParentheses()
          case other => other
        }

        element.innerElement
          .map(RemoveBracesIntention.collectComments(_))
          .foreach(RemoveBracesIntention.addComments(_, replaced.getParent, replaced))

        ScalaPsiUtil padWithWhitespaces replaced
      }
    }
    registerProblem(parenthesized, foo, holder, isOnTheFly)
  }

  private def registerProblem(elt: ScParameterClause, holder: ProblemsHolder, isOnTheFly: Boolean): Unit = {
    val quickFix = new AbstractFixOnPsiElement[ScParameterClause](ScalaInspectionBundle.message("remove.unnecessary.parentheses.with.text", getShortText(elt)), elt) {
      override protected def doApplyFix(element: ScParameterClause)(implicit project: Project): Unit = {
        if (isParenthesised(element)) {
          element.getNode.removeChild(element.getNode.getFirstChildNode)
          element.getNode.removeChild(element.getNode.getLastChildNode)
        }
      }
    }

    registerProblem(elt, quickFix, holder, isOnTheFly)
  }

  private def registerProblem(elt: ScalaPsiElement, qf: LocalQuickFix, holder: ProblemsHolder, isOnTheFly: Boolean): Unit = {
    registerRedundantParensProblem(ScalaInspectionBundle.message("displayname.unnecessary.parentheses"), elt, qf, holder, isOnTheFly)
  }
}
