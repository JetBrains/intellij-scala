package org.jetbrains.plugins.scala.codeInspection.SAM

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.SAM.ConvertExpressionToSAMInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection, InspectionBundle}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 6/29/15
 */
class ConvertExpressionToSAMInspection extends AbstractInspection(inspectionId, inspectionName) {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case definition: ScNewTemplateDefinition if ScalaPsiUtil.isSAMEnabled(definition) =>
      definition.expectedTypes().toSeq.flatMap {
        ScalaPsiUtil.toSAMType(_, definition)
      } match {
        case Seq(expectedMethodType) => inspectAccordingToExpectedType(expectedMethodType, definition, holder)
        case _ =>
      }
  }

  private def inspectAccordingToExpectedType(expected: ScType, definition: ScNewTemplateDefinition, holder: ProblemsHolder) {
    definition.members match {
      case Seq(fun: ScFunctionDefinition) =>
        def containsReturn(expr: ScExpression): Boolean = {
          expr.depthFirst().exists(_.getNode.getElementType == ScalaTokenTypes.kRETURN)
        }
        fun.body match {
          case Some(funBody) if fun.getType().getOrAny.conforms(expected) && !containsReturn(funBody) =>
            lazy val replacement: String = {
              val res = new StringBuilder
              val isInfix = definition.parent.exists(_.isInstanceOf[ScInfixExpr])
              if (isInfix) {
                res.append("(")
              }
              fun.effectiveParameterClauses.headOption match {
                case Some(paramClause) =>
                  res.append(cleanedParamsText(paramClause))
                  res.append(" => ")
                case _ =>
              }
              res.append(funBody.getText)
              if (isInfix) {
                res.append(")")
              }
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
    element.replace(createExpressionFromText(replacement)(element.getManager))
  }
}

object ConvertExpressionToSAMInspection {
  val inspectionName = InspectionBundle.message("convert.expression.to.sam")
  val inspectionId = "ConvertExpressionToSAM"
}
