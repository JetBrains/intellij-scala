package org.jetbrains.plugins.scala.codeInspection.SAM

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.SAM.ConvertExpressionToSAMInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection, InspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 6/29/15
 */
class ConvertExpressionToSAMInspection extends AbstractInspection(inspectionId, inspectionName) {
  override def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case param: ScNewTemplateDefinition if ScalaPsiUtil.isSAMEnabled(param) =>
      for (expected <- param.expectedTypes()) {
        inspectAccordingToExpectedType(expected, param, holder)
      }
  }

  private def inspectAccordingToExpectedType(expected: ScType, definition: ScNewTemplateDefinition, holder: ProblemsHolder) {
    ScalaPsiUtil.toSAMType(expected) match {
      case Some(expectedMethodType) =>
        val funChildIt = definition.breadthFirst.filter(_.isInstanceOf[ScFunctionDefinition])
        if (funChildIt.hasNext) {
          val actualFun = funChildIt.next().asInstanceOf[ScFunctionDefinition]
          actualFun.body match {
            case Some(body) if expectedMethodType.conforms(actualFun.getType().getOrNothing) =>
              lazy val replacement: String = {
                val res = new StringBuilder
                actualFun.effectiveParameterClauses.headOption match {
                  case Some(paramClause) =>
                    res.append(paramClause.getText)
                    res.append(" => ")
                  case _ =>
                }
                res.append(actualFun.body.getOrElse(actualFun).getText)
                res.toString()
              }
              val fix = new ReplaceExpressionWithSAMQuickFix(definition, replacement)
              holder.registerProblem(definition, inspectionName,
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fix)
            case _ =>
          }
        }
      case _ =>
    }
  }
}

class ReplaceExpressionWithSAMQuickFix(elem: PsiElement, replacement: => String) extends AbstractFixOnPsiElement(
  inspectionName, elem) {
  override def doApplyFix(project: Project): Unit = {
    val element = getElement
    if (!element.isValid) return
    element.replace(ScalaPsiElementFactory.createExpressionFromText(replacement, element.getContext))
  }
}

object ConvertExpressionToSAMInspection {
  val inspectionName = InspectionBundle.message("convert.expression.to.sam")
  val inspectionId = "ConvertExpressionToSAM"
}
