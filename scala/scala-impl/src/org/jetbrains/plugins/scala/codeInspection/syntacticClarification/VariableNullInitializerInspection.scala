package org.jetbrains.plugins.scala.codeInspection.syntacticClarification

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.syntacticClarification.VariableNullInitializerInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createTypeElementFromText}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api._

class VariableNullInitializerInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case definition: ScVariableDefinition if definition.isDefinedInClass =>
      if (definition.declaredType.exists(isApplicable)) {
        definition.expr.filter(e => e.isValid && isNull(e)).foreach { expression =>
          holder.registerProblem(expression, Message, new UseUnderscoreInitializerQuickFix(definition), new UseOptionTypeQuickFix(definition))
        }
      }
    case _ =>
  }
}

private object VariableNullInitializerInspection {
  private val Message = ScalaInspectionBundle.message("variable.with.null.initializer")

  private def isApplicable(tpe: ScType): Boolean = tpe match {
    case t: ValType if !t.isUnit => false
    case _ => true
  }

  private def isNull(expr: ScExpression): Boolean =
    Option(expr)
      .flatMap(_.firstChild)
      .flatMap(element => Option(element.getNode))
      .map(_.getElementType)
      .exists {
        case ScalaTokenTypes.kNULL => true
        case _ => false
      }

  private class UseUnderscoreInitializerQuickFix(definition: ScVariableDefinition) extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("use.underscore.initializer"), definition) {
    override protected def doApplyFix(element: ScVariableDefinition)(implicit project: Project): Unit =
      element.expr.filter(isNull).foreach(_.replace(createExpressionFromText("_")))
  }

  private class UseOptionTypeQuickFix(definition: ScVariableDefinition) extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("use.option.type"), definition) {
    override protected def doApplyFix(element: ScVariableDefinition)(implicit project: Project): Unit = {
      element.expr.filter(isNull).foreach(_.replace(createExpressionFromText("None")))
      element.typeElement.foreach(typeElement => typeElement.replace(createTypeElementFromText(s"Option[${typeElement.getText}]")))
    }
  }
}
