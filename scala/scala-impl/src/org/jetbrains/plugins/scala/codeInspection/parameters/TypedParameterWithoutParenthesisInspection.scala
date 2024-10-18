package org.jetbrains.plugins.scala.codeInspection.parameters

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.{LocalInspectionTool, LocalQuickFix, ProblemsHolder}
import com.intellij.openapi.project.{DumbAware, Project}
import org.jetbrains.plugins.scala.codeInspection.parameters.TypedParameterWithoutParenthesisInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

final class TypedParameterWithoutParenthesisInspection extends LocalInspectionTool with DumbAware {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case parameterClause: ScParameterClause if !parameterClause.hasParenthesis && parameterClause.parameters.sizeIs == 1 =>
      for {
        param <- parameterClause.parameters
        _     <- param.typeElement
      } holder.registerProblem(parameterClause, getDisplayName, createQuickFix(parameterClause))
    case _ =>
  }
}

object TypedParameterWithoutParenthesisInspection {
  private def createQuickFix(pc: ScParameterClause): LocalQuickFix = new AbstractFixOnPsiElement(ScalaInspectionBundle.message("surround.with.parenthesis"), pc)
    with HighPriorityAction
    with DumbAware {
    override protected def doApplyFix(pclause: ScParameterClause)
                                     (implicit project: Project): Unit = {
      val replacement = ScalaPsiElementFactory.createExpressionFromText("(" + pclause.getText + ")", pclause)
      pclause.replace(replacement)
    }
  }
}
