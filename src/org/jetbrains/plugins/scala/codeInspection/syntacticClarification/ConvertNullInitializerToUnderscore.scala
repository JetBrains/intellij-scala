package org.jetbrains.plugins.scala.codeInspection.syntacticClarification

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.syntacticClarification.ConvertNullInitializerToUnderscore._
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
    holder.registerProblem(variable.expr.get, inspectionName, fix)
  }

  private def isNull(expr: ScExpression): Boolean = {
    expr.getFirstChild.getNode.getElementType == ScalaTokenTypes.kNULL
  }

  override def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case variable: ScVariableDefinition if !variable.isLocal && variable.expr.nonEmpty && variable.hasExplicitType =>
      variable.declaredType.get match {
        case valType: api.ValType if valType ne api.Unit =>
        case declaredType  =>
          val expr = variable.expr.get
          if (expr.isValid && isNull(expr))
              registerProblem(holder, variable)
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