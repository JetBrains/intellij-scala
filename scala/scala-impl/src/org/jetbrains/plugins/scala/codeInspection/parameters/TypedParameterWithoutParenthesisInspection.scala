package org.jetbrains.plugins.scala
package codeInspection
package parameters

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.{LocalInspectionTool, LocalQuickFix, ProblemsHolder}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.parameters.TypedParameterWithoutParenthesisInspection._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

class TypedParameterWithoutParenthesisInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case parameterClause: ScParameterClause if !parameterClause.hasParenthesis && parameterClause.parameters.size == 1 =>
      parameterClause.parameters.head.typeElement.foreach { _ =>
        holder.registerProblem(parameterClause, getDisplayName, createQuickFix(parameterClause))
      }
    case _ =>
  }
}

object TypedParameterWithoutParenthesisInspection {
  private def createQuickFix(pc: ScParameterClause): LocalQuickFix = new AbstractFixOnPsiElement(ScalaInspectionBundle.message("surround.with.parenthesis"), pc) with HighPriorityAction {
    override protected def doApplyFix(pclause: ScParameterClause)
                                     (implicit project: Project): Unit = {
      val replacement = ScalaPsiElementFactory.createExpressionFromText("(" + pclause.getText + ")")
      pclause.replace(replacement)
    }
  }
}