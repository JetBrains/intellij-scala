package org.jetbrains.plugins.scala.codeInspection.SAM

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.SAM.ConvertExpressionToSAMInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection, InspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.project.ProjectExt

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

  private def inspectAccordingToExpectedType(expected: ScType, definition: ScNewTemplateDefinition, holder: ProblemsHolder)
                                            (implicit typeSystem: TypeSystem = holder.getProject.typeSystem) {
    ScalaPsiUtil.toSAMType(expected, definition.getResolveScope) match {
      case Some(expectedMethodType) =>
        definition.members match {
          case Seq(fun: ScFunctionDefinition) =>
            fun.body match {
              case Some(funBody) if expectedMethodType.conforms(fun.getType().getOrNothing) =>
                lazy val replacement: String = {
                  val res = new StringBuilder
                  fun.effectiveParameterClauses.headOption match {
                    case Some(paramClause) =>
                      res.append(cleanedParamsText(paramClause))
                      res.append(" => ")
                    case _ =>
                  }
                  res.append(funBody.getText)
                  res.toString()
                }
                val fix = new ReplaceExpressionWithSAMQuickFix(definition, replacement)
                val extendsBlock = definition.extendsBlock
                val lBraceInParent = extendsBlock.templateBody.map(_.startOffsetInParent + extendsBlock.startOffsetInParent)
                val rangeInElement: TextRange = lBraceInParent.map(new TextRange(0, _)).orNull
                holder.registerProblem(definition, inspectionName, ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                  rangeInElement, fix)
              case _ =>
            }
          case _ =>
        }
      case _ =>
    }
  }

  //to get rid of by-name and default params
  private def cleanedParamsText(paramClause: ScParameterClause) = {
    val parameters = paramClause.parameters
    val namesWithTypes = parameters.map { p =>
      val name = p.name
      val typeText = p.typeElement.map(_.getText).getOrElse("")
      s"$name: $typeText"
    }
    namesWithTypes.mkString("(", ", ", ")")
  }
}

class ReplaceExpressionWithSAMQuickFix(elem: PsiElement, replacement: => String)
  extends AbstractFixOnPsiElement(inspectionName, elem) {
  
  override def doApplyFix(project: Project): Unit = {
    val element = getElement
    if (!element.isValid) return
    element.replace(ScalaPsiElementFactory.createExpressionFromText(replacement, element.getManager))
  }
}

object ConvertExpressionToSAMInspection {
  val inspectionName = InspectionBundle.message("convert.expression.to.sam")
  val inspectionId = "ConvertExpressionToSAM"
}
