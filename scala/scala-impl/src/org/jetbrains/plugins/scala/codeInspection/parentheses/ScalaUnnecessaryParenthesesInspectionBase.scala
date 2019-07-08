package org.jetbrains.plugins.scala
package codeInspection
package parentheses

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.codeInsight.intention.RemoveBracesIntention
import org.jetbrains.plugins.scala.extensions.ParenthesizedElement.Ops
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScParenthesizedElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScFunctionalTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker.checkInspection

/**
 * Nikolay.Tropin
 * 4/25/13
 */
abstract class ScalaUnnecessaryParenthesesInspectionBase extends LocalInspectionTool
  with RedundantBracketsInspectionLike {

  import ScalaUnnecessaryParenthesesInspectionBase._

  override final def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = new PsiElementVisitor {

    override def visitElement(element: PsiElement): Unit = element match {
      case parenthesized: ScParenthesizedElement if isProblem(parenthesized) =>
        registerRedundantParensProblem(
          parenthesized,
          new UnnecessaryExpressionParenthesesQuickFix(parenthesized)
        )
      case expression: ScFunctionExpr if hasSingleParenthesizedParam(expression) && !currentSettings.ignoreAroundFunctionExprParam =>
        for {
          clause <- expression.params.clauses.headOption
          quickFix = new UnnecessaryClauseParenthesisQuickFix(clause)
        } registerRedundantParensProblem(clause, quickFix)
      case _ =>
    }

    private def registerRedundantParensProblem[E <: ScalaPsiElement](element: E,
                                                                     quickFix: UnnecessaryParenthesesQuickFix[E]): Unit =
      ScalaUnnecessaryParenthesesInspectionBase.this
        .registerRedundantParensProblem(getDisplayName, element, quickFix)(holder, isOnTheFly)
  }

  def currentSettings: UnnecessaryParenthesesSettings

  @TestOnly
  def setSettings(settings: UnnecessaryParenthesesSettings): Unit

  private def hasSingleParenthesizedParam(f: ScFunctionExpr): Boolean = {
    // If an anonymous function (x: T) => e with a single typed parameter appears as the result expression of a block, it can be abbreviated to x: T => e.
    def isBlockResultExpr: Boolean = f.getParent match {
      case block: ScBlockExpr if block.resultExpression.contains(f) => true
      case _ => false
    }

    // In the case of a single untyped formal parameter, (x) => e can be abbreviated to x => e
    def hasNoParamType = f.parameters.headOption.exists(_.typeElement.isEmpty)

    def hasParenthesizedClause = f.params.clauses
      .headOption
      .map(_.getNode)
      .exists(isParenthesised)

    f.parameters.size == 1 && hasParenthesizedClause && (hasNoParamType || isBlockResultExpr)
  }

  private def isParenthesesRedundant(parenthesized: ScParenthesizedElement,
                                     settings: UnnecessaryParenthesesSettings): Boolean = {
    if (parenthesized.isParenthesisNeeded) return false

    parenthesized match {
      case _ if parenthesized.isParenthesisClarifying && settings.ignoreClarifying => false
      case ScParenthesizedElement(_: ScFunctionalTypeElement) if settings.ignoreAroundFunctionType => false
      case _ if parenthesized.isFunctionTypeSingleParam && settings.ignoreAroundFunctionTypeParam => false
      case _ => true
    }
  }

  private def isProblem(parenthesized: ScParenthesizedElement): Boolean =
    !parenthesized.isNestedParenthesis &&
      checkInspection(this, parenthesized) &&
      isParenthesesRedundant(parenthesized, currentSettings)
}

object ScalaUnnecessaryParenthesesInspectionBase {

  import RemoveBracesIntention._
  import ScalaTokenTypes._

  private sealed abstract class UnnecessaryParenthesesQuickFix[E <: ScalaPsiElement](element: E) extends AbstractFixOnPsiElement(
    InspectionBundle.message("remove.unnecessary.parentheses.fix", ScalaRefactoringUtil.getShortText(element)),
    element
  )

  private final class UnnecessaryExpressionParenthesesQuickFix(parenthesized: ScParenthesizedElement)
    extends UnnecessaryParenthesesQuickFix(parenthesized) {

    override protected def doApplyFix(parenthesized: ScParenthesizedElement)
                                     (implicit project: Project): Unit = {
      val keepParentheses = parenthesized.isNestingParenthesis
      // remove first the duplicate parentheses
      val replaced = parenthesized.doStripParentheses(keepParentheses) match {
        // Remove the last level of parentheses if allowed
        case paren: ScParenthesizedElement if paren.isParenthesisRedundant => paren.doStripParentheses()
        case other => other
      }

      parenthesized.innerElement
        .map(collectComments(_))
        .foreach(addComments(_, replaced.getParent, replaced))

      ScalaPsiUtil.padWithWhitespaces(replaced)
    }
  }

  private final class UnnecessaryClauseParenthesisQuickFix(clause: ScParameterClause)
    extends UnnecessaryParenthesesQuickFix(clause) {

    override protected def doApplyFix(clause: ScParameterClause)
                                     (implicit project: Project): Unit = {
      val node = clause.getNode
      if (isParenthesised(node)) {
        node.removeChild(node.getFirstChildNode)
        node.removeChild(node.getLastChildNode)
      }
    }
  }

  private def isParenthesised(node: ASTNode): Boolean =
    node.getFirstChildNode.getElementType == tLPARENTHESIS &&
      node.getLastChildNode.getElementType == tRPARENTHESIS
}