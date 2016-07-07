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

  override def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case variable: ScVariableDefinition if !variable.isLocal && variable.expr.nonEmpty && variable.hasExplicitType =>
      variable.declaredType.get match {
        case valType: api.ValType if valType ne api.Unit =>
        case declaredType  =>
          val expr = variable.expr.get
          if (expr.isValid && ConvertNullInitializerToUnderscore.isNull(expr))
              registerProblem(holder, variable)
      }
  }
}

class ConvertNullInitializerToUnderscoreQuickFix(e: ScVariableDefinition)
  extends AbstractFixOnPsiElement(inspectionName, e) {

  override def doApplyFix(project: Project): Unit = {
    val variable: ScVariableDefinition = getElement
    if (variable == null) return

    val psiManager = variable.getManager
    variable.expr match {
      case Some(expr) if ConvertNullInitializerToUnderscore.isNull(expr) =>
        val under = ScalaPsiElementFactory.createExpressionFromText("_", psiManager)
        expr.replace(under)
      case _ =>
    }
  }
}

object ConvertNullInitializerToUnderscore {
  val inspectionName = InspectionBundle.message("convert.null.initializer.to.underscore")
  val inspectionId = "ScalaConvertNullInitializerToUnderscore"

  def isNull(expr: ScExpression): Boolean = {
    expr.getFirstChild.getNode.getElementType == ScalaTokenTypes.kNULL
  }
}