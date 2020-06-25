package org.jetbrains.plugins.scala
package codeInspection
package parameters

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.parameters.TypedParameterWithoutParenthesisInspection._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

class TypedParameterWithoutParenthesisInspection extends AbstractRegisteredInspection {
  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] = {
    for {
      pc <- element.asOptionOf[ScParameterClause]
      if !pc.hasParenthesis
      Seq(parem) <- Some(pc.parameters)
      if parem.typeElement.isDefined
      desc <- super.problemDescriptor(element, Some(createQuickFix(pc)), descriptionTemplate, highlightType)
    } yield desc
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