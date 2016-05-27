package org.jetbrains.plugins.scala.codeInspection.convertNullInitializerToUnderscore

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.convertNullInitializerToUnderscore.ConvertNullInitializerToUnderscore._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection, InspectionBundle}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.api

/**
  * Created by a.tsukanov on 26.05.2016.
  */
class ConvertNullInitializerToUnderscore extends AbstractInspection(inspectionId, inspectionName) {
  private def registerProblem(holder: ProblemsHolder, variable: ScVariableDefinition): Unit = {
    val fix = new ConvertNullInitializerToUnderscoreQuickFix(variable)
    holder.registerProblem(variable, inspectionName, ProblemHighlightType.WEAK_WARNING, fix)
  }

  private def isNull(expr: ScExpression): Boolean = {
    expr.getFirstChild.getNode.getElementType == ScalaTokenTypes.kNULL
  }

  override def actionFor(holder: ProblemsHolder) = {
    case variable: ScVariableDefinition if variable.expr.nonEmpty && variable.hasExplicitType =>
      variable.declaredType.get match {
        case valType: api.ValType if valType ne api.Unit =>
        case declaredType  =>
          val expr = variable.expr.get
          if (expr.isValid) {
            val exprIsNull = expr.getTypeWithoutImplicits().exists(_ eq api.Null)
            val unitIsNull = (declaredType eq api.Unit) && isNull(expr)

            if (exprIsNull || unitIsNull)
              registerProblem(holder, variable)
          }
      }
  }
}

class ConvertNullInitializerToUnderscoreQuickFix(variable: ScVariableDefinition)
  extends AbstractFixOnPsiElement(inspectionName, variable) {

  override def doApplyFix(project: Project): Unit = {
    val psiManager = variable.getManager
    val expression = ScalaPsiElementFactory.createExpressionFromText("_", psiManager)
    val name = variable.declaredNames.mkString(", ")
    val typeName = variable.typeElement.get.text

    variable.replace(ScalaPsiElementFactory.createDeclaration(name, typeName, isVariable = true, expression, psiManager))
  }
}

object ConvertNullInitializerToUnderscore {
  val inspectionName = InspectionBundle.message("convert.null.initializer.to.underscore")
  val inspectionId = "ScalaConvertNullInitializerToUnderscore"
}