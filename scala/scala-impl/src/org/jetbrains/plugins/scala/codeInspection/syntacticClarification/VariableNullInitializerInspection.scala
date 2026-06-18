package org.jetbrains.plugins.scala.codeInspection.syntacticClarification

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.codeInspection.syntacticClarification.VariableNullInitializerInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiFileExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createTypeElementFromText}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.project.ScalaFeatures.forPsiOrDefault

class VariableNullInitializerInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = PsiElementVisitorSimple(holder) {
    case definition: ScVariableDefinition if definition.isDefinedInClass =>
      if (definition.declaredType.exists(isApplicable)) {
        val nullBodyExpr = definition.expr.filter(e => e.isValid && isNull(e))
        nullBodyExpr.foreach { expression =>
          val fixes = buildFixesFor(definition)
          holder.registerProblem(expression, Message, fixes: _*)
        }
      }
    case _ =>
  }

  private def buildFixesFor(definition: ScVariableDefinition): Seq[AbstractFixOnPsiElement[ScVariableDefinition]] = {
    val lateInitializerFix =
      if (definition.isInScala3File)
        new UseCompiletimeUninitializedQuickFix(definition)
      else
        new UseUnderscoreInitializerQuickFix(definition)

    Seq(
      lateInitializerFix,
      new UseOptionTypeQuickFix(definition),
    )
  }
}

private object VariableNullInitializerInspection {
  private val Message = ScalaInspectionBundle.message("variable.with.null.initializer")

  private def isApplicable(tpe: ScType): Boolean = tpe match {
    case t: ValType if !t.isUnit => false
    case _ => true
  }

  private def isNull(bodyExpr: ScExpression): Boolean =
    Option(bodyExpr)
      .flatMap(_.firstChild)
      .flatMap(element => Option(element.getNode))
      .map(_.getElementType)
      .exists {
        case ScalaTokenTypes.kNULL => true
        case _ => false
      }

  private class UseUnderscoreInitializerQuickFix(definition: ScVariableDefinition) extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("use.underscore.initializer"), definition) {
    override protected def doApplyFix(element: ScVariableDefinition)(implicit project: Project): Unit =
      element.expr.filter(isNull).foreach(_.replace(createExpressionFromText("_", element)))
  }

  private class UseOptionTypeQuickFix(definition: ScVariableDefinition) extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("use.option.type"), definition) {
    override protected def doApplyFix(element: ScVariableDefinition)(implicit project: Project): Unit = {
      element.expr.filter(isNull).foreach(_.replace(createExpressionFromText("None", element)))
      element.typeElement.foreach(typeElement => typeElement.replace(createTypeElementFromText(s"Option[${typeElement.getText}]", definition)))
    }
  }

  private class UseCompiletimeUninitializedQuickFix(definition: ScVariableDefinition)
    extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("use.compiletime.uninitialized"), definition) {

    override protected def doApplyFix(element: ScVariableDefinition)(implicit project: Project): Unit =
      element.expr.filter(isNull).foreach { expression =>
        expression.replace(createExpressionFromText("uninitialized", element))
        ScImportsHolder(element).addImportForPath("scala.compiletime.uninitialized", element)
      }
  }
}
