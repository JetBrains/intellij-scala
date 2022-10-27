package org.jetbrains.plugins.scala.codeInspection.typeAnnotation

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScFunctionExpr, ScTypedExpression, ScUnderscoreSection}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.settings.annotations._
import org.jetbrains.plugins.scala.util._

import scala.annotation.nowarn

class TypeAnnotationInspection extends LocalInspectionTool {
  import TypeAnnotationInspection._

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case value: ScPatternDefinition if value.isSimple && !value.hasExplicitType =>
      inspect(value, value.bindings.head, value.expr, holder)
    case variable: ScVariableDefinition if variable.isSimple && !variable.hasExplicitType =>
      inspect(variable, variable.bindings.head, variable.expr, holder)
    case method: ScFunctionDefinition if method.hasAssign && !method.hasExplicitType && !method.isConstructor =>
      inspect(method, method.nameId, method.body, holder)
    case (parameter: ScParameter) && Parent(Parent(Parent(_: ScFunctionExpr))) if parameter.typeElement.isEmpty =>
      inspect(parameter, parameter.nameId, implementation = None, holder)
    case (underscore: ScUnderscoreSection) && Parent(parent) if underscore.getTextRange.getLength == 1 &&
      !parent.isInstanceOf[ScTypedExpression] && !parent.isInstanceOf[ScFunctionDefinition] &&
      !parent.isInstanceOf[ScPatternDefinition] && !parent.isInstanceOf[ScVariableDefinition] =>
      inspect(underscore, underscore, implementation = None, holder)
    case _ =>
  }
}

object TypeAnnotationInspection {

  def highlightKey: HighlightDisplayKey = HighlightDisplayKey.find("TypeAnnotation")

  def getReasonForTypeAnnotationOn(element: ScalaPsiElement, implementation: Option[ScExpression]): Option[String] = {
    val declaration = Declaration(element)
    val location = Location(element)

    ScalaTypeAnnotationSettings(element.getProject).reasonForTypeAnnotationOn(
      declaration, location, implementation.map(Expression))
  }

  private def inspect(element: ScalaPsiElement,
                      anchor: PsiElement,
                      implementation: Option[ScExpression],
                      holder: ProblemsHolder): Unit = {

    getReasonForTypeAnnotationOn(element, implementation).foreach { reason =>

      val fixes =
          Seq(new AddTypeAnnotationQuickFix(anchor), new ModifyCodeStyleQuickFix())

      holder.registerProblem(anchor, ScalaInspectionBundle.message("type.annotation.required.for", reason), fixes: _*)
    }
  }

  @nowarn("cat=deprecation")
  private class ModifyCodeStyleQuickFix extends LocalQuickFixBase(ScalaInspectionBundle.message("quickfix.modify.code.style")) {
    override def applyFix(project: Project, problemDescriptor: ProblemDescriptor): Unit =
      TypeAnnotationUtil.showTypeAnnotationsSettings(project)

    override def generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo =
      IntentionPreviewInfo.EMPTY
  }
}
