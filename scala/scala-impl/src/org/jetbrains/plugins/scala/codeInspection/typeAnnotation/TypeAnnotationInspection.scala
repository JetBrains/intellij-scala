package org.jetbrains.plugins.scala
package codeInspection
package typeAnnotation

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection._
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScFunctionExpr, ScTypedExpression, ScUnderscoreSection}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
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

  private def inspect(element: ScalaPsiElement,
                      anchor: PsiElement,
                      implementation: Option[ScExpression],
                      holder: ProblemsHolder): Unit = {

    val declaration = Declaration(element)
    val location = Location(element)

    ScalaTypeAnnotationSettings(element.getProject).reasonForTypeAnnotationOn(
      declaration, location, implementation.map(Expression)).foreach { reason =>

      // TODO Create the general-purpose inspection
      val canBePrivate = !declaration.entity.isParameter &&
        !location.isInLocalScope && declaration.visibility == Visibility.Default

      val fixes =
        canBePrivate.seq(new MakePrivateQuickFix(element.asInstanceOf[ScModifierListOwner])) ++
          Seq(new AddTypeAnnotationQuickFix(anchor), new ModifyCodeStyleQuickFix(), new LearnWhyQuickFix())

      holder.registerProblem(anchor, ScalaInspectionBundle.message("type.annotation.required.for", reason), fixes: _*)
    }
  }

  private class MakePrivateQuickFix(element: ScModifierListOwner) extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("quickfix.make.private"), element) {

    override protected def doApplyFix(element: ScModifierListOwner)
                                     (implicit project: Project): Unit = {
      element.setModifierProperty("private")
    }
  }

  @nowarn("cat=deprecation")
  private class LearnWhyQuickFix extends LocalQuickFixBase(ScalaInspectionBundle.message("learn.why")) {
    override def applyFix(project: Project, problemDescriptor: ProblemDescriptor): Unit =
      BrowserUtil.browse("https://blog.jetbrains.com/scala/2016/10/05/beyond-code-style/")

    override def startInWriteAction(): Boolean = false
  }

  @nowarn("cat=deprecation")
  private class ModifyCodeStyleQuickFix extends LocalQuickFixBase(ScalaInspectionBundle.message("quickfix.modify.code.style")) {
    override def applyFix(project: Project, problemDescriptor: ProblemDescriptor): Unit =
      TypeAnnotationUtil.showTypeAnnotationsSettings(project)

    override def generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo =
      IntentionPreviewInfo.EMPTY
  }
}
