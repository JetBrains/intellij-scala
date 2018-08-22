package org.jetbrains.plugins.scala
package codeInspection
package controlFlow

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.{IntentionAvailabilityChecker, SideEffectsUtil}

/**
  * Nikolay.Tropin
  * 2014-09-22
  */
final class ScalaUnusedExpressionInspection extends AbstractRegisteredInspection {

  import ScalaUnusedExpressionInspection._
  import SideEffectsUtil.{hasNoSideEffects, mayOnlyThrow}

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] =
    element match {
      case expression: ScExpression if IntentionAvailabilityChecker.checkInspection(this, expression.getParent) &&
        canResultInSideEffectsOnly(expression) =>

        for {
          descriptionTemplate <- if (hasNoSideEffects(expression)) Some(InspectionBundle.message("unused.expression.no.side.effects"))
          else if (mayOnlyThrow(expression)) Some(InspectionBundle.message("unused.expression.throws"))
          else None

        } yield manager.createProblemDescriptor(expression, descriptionTemplate, isOnTheFly, createQuickFixes(expression), highlightType)
      case _ => None
    }
}

object ScalaUnusedExpressionInspection {

  private def canResultInSideEffectsOnly(expression: ScExpression): Boolean =
    isNonLastInBlock(expression) ||
      parents(expression).exists {
        case e: ScExpression => isNonLastInBlock(e)
        case _ => false
      } ||
      isInUnitFunctionReturnPosition(expression)

  private def findDefiningFunction(expression: ScExpression) =
    expression.parentOfType(classOf[ScFunctionDefinition])

  private def isUnit(definition: ScFunctionDefinition) =
    definition.returnType.exists(_.isUnit)

  private def createQuickFixes(expression: ScExpression): Array[LocalQuickFix] = new RemoveExpressionQuickFix(expression) match {
    case quickFix if findDefiningFunction(expression).forall(isUnit) => Array(quickFix)
    case quickFix => Array(quickFix, new AddReturnQuickFix(expression))
  }

  private[this] class AddReturnQuickFix(expression: ScExpression) extends AbstractFixOnPsiElement(
    "Add return keyword",
    expression
  ) {
    override protected def doApplyFix(expression: ScExpression)
                                     (implicit project: Project): Unit = {
      val retStmt = ScalaPsiElementFactory.createExpressionWithContextFromText(s"return ${expression.getText}", expression.getContext, expression)
      expression.replaceExpression(retStmt, removeParenthesis = true)
    }
  }

  private[this] class RemoveExpressionQuickFix(expression: ScExpression) extends AbstractFixOnPsiElement(
    "Remove expression",
    expression
  ) {
    override protected def doApplyFix(expression: ScExpression)
                                     (implicit project: Project): Unit = {
      expression.delete()
    }
  }

  private[this] def isNonLastInBlock(expression: ScExpression) = expression.getParent match {
    case block: ScBlock => !block.lastExpr.contains(expression)
    case _ => false
  }

  private[this] def parents(expression: ScExpression) = {
    def isNotAncestor(maybeExpression: Option[ScExpression]) =
      maybeExpression.forall(!PsiTreeUtil.isAncestor(_, expression, false))

    expression.parentsInFile.takeWhile {
      case statement: ScMatchStmt => isNotAncestor(statement.expr)
      case statement: ScIfStmt => isNotAncestor(statement.condition)
      case _: ScBlock |
           _: ScParenthesisedExpr |
           _: ScCaseClause |
           _: ScCaseClauses |
           _: ScTryStmt |
           _: ScCatchBlock => true
      case _ => false
    }
  }

  private[this] def isInUnitFunctionReturnPosition(expression: ScExpression) = {
    findDefiningFunction(expression).exists { definition =>
      isUnit(definition) && definition.returnUsages(expression)
    }
  }
}