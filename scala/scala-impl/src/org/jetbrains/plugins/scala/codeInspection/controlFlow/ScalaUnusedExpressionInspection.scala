package org.jetbrains.plugins.scala
package codeInspection
package controlFlow

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.quickfix.RemoveExpressionQuickFix
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.{IntentionAvailabilityChecker, SideEffectsUtil}

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
        expressionResultIsNotUsed(expression) =>

        for {
          descriptionTemplate <- if (hasNoSideEffects(expression)) Some(ScalaInspectionBundle.message("unused.expression.no.side.effects"))
          else if (mayOnlyThrow(expression)) Some(ScalaInspectionBundle.message("unused.expression.throws"))
          else None

        } yield manager.createProblemDescriptor(expression, descriptionTemplate, isOnTheFly, createQuickFixes(expression), ProblemHighlightType.LIKE_UNUSED_SYMBOL)
      case _ => None
    }
}

object ScalaUnusedExpressionInspection {

  private def createQuickFixes(expression: ScExpression): Array[LocalQuickFix] = new RemoveExpressionQuickFix(expression) match {
    case quickFix if findDefiningFunction(expression).forall(isUnitFunction) => Array(quickFix)
    case quickFix => Array(quickFix, new AddReturnQuickFix(expression))
  }

  private[this] class AddReturnQuickFix(expression: ScExpression) extends AbstractFixOnPsiElement(
    ScalaInspectionBundle.message("add.return.keyword"),
    expression
  ) {
    override protected def doApplyFix(expression: ScExpression)
                                     (implicit project: Project): Unit = {
      val retStmt = ScalaPsiElementFactory.createExpressionWithContextFromText(s"return ${expression.getText}", expression.getContext, expression)
      expression.replaceExpression(retStmt, removeParenthesis = true)
    }
  }
}
