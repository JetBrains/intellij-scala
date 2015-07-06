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
    ScalaPsiUtil.toSAMType(expected, definition.getResolveScope) match {
      case Some(expectedMethodType) =>
        definition.members match {
          case Seq(fun: ScFunctionDefinition) =>
            fun.body match {
              case Some(body) if expectedMethodType.conforms(fun.getType().getOrNothing) =>
                lazy val replacement: String = {
                  val res = new StringBuilder
                  fun.effectiveParameterClauses.headOption match {
                    case Some(paramClause) =>
                      res.append(paramClause.getText)
                      res.append(" => ")
                    case _ =>
                  }
                  res.append(fun.body.getOrElse(fun).getText)
                  res.toString()
                }
                val fix = new ReplaceExpressionWithSAMQuickFix(definition, replacement)
                holder.registerProblem(definition, inspectionName,
                  ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fix)
              case _ =>
            }
          case _ =>
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
