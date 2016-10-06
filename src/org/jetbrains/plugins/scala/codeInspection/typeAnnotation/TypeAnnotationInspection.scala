package org.jetbrains.plugins.scala
package codeInspection.typeAnnotation

import com.intellij.codeInspection.{LocalQuickFixBase, ProblemDescriptor, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.intention.types.{AddOnlyStrategy, ToggleTypeAnnotation}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.lang.formatting.settings._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.TypeAnnotationUtil

/**
 * Pavel Fatin
 */
class TypeAnnotationInspection extends AbstractInspection {
  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case value: ScPatternDefinition if value.isSimple && !value.hasExplicitType =>
      val settings = ScalaCodeStyleSettings.getInstance(holder.getProject)

      inspect(value.bindings.head,
        kindOf(value) + " value",
        TypeAnnotationUtil.isOverriding(value),
        value.expr.exists(TypeAnnotationUtil.isSimple),
        TypeAnnotationUtil.requirementForProperty(value, settings),
        settings.OVERRIDING_PROPERTY_TYPE_ANNOTATION,
        settings.SIMPLE_PROPERTY_TYPE_ANNOTATION,
        holder)

    case variable: ScVariableDefinition if variable.isSimple && !variable.hasExplicitType =>
      val settings = ScalaCodeStyleSettings.getInstance(holder.getProject)

      inspect(variable.bindings.head,
        kindOf(variable) + " variable",
        TypeAnnotationUtil.isOverriding(variable),
        variable.expr.exists(TypeAnnotationUtil.isSimple),
        TypeAnnotationUtil.requirementForProperty(variable, settings),
        settings.OVERRIDING_PROPERTY_TYPE_ANNOTATION,
        settings.SIMPLE_PROPERTY_TYPE_ANNOTATION,
        holder)

    case method: ScFunctionDefinition if method.hasAssign && !method.hasExplicitType && !method.isSecondaryConstructor =>
      val settings = ScalaCodeStyleSettings.getInstance(holder.getProject)

      inspect(method.nameId,
        kindOf(method) + " method",
        TypeAnnotationUtil.isOverriding(method),
        method.body.exists(TypeAnnotationUtil.isSimple),
        TypeAnnotationUtil.requirementForMethod(method, settings),
        settings.OVERRIDING_METHOD_TYPE_ANNOTATION,
        settings.SIMPLE_METHOD_TYPE_ANNOTATION,
        holder)
  }

  private def kindOf(member: ScMember) = if (member.isLocal) "Local" else {
    if (member.isPrivate) "Private" else if (member.isProtected) "Protected" else "Public"
  }

  private def inspect(element: PsiElement,
                      name: String,
                      isOverriding: Boolean,
                      isSimple: Boolean,
                      requirement: Int,
                      overridingPolicy: Int,
                      simplePolicy: Int,
                      holder: ProblemsHolder) {
    if (requirement == TypeAnnotationRequirement.Required.ordinal &&
            (!isSimple || simplePolicy == TypeAnnotationPolicy.Regular.ordinal) &&
            (overridingPolicy == TypeAnnotationPolicy.Regular.ordinal || !isOverriding)) {
      holder.registerProblem(element, s"$name requires an explicit type annotation (according to Code Style settings)",
        new AddTypeAnnotationQuickFix(element),
        new LearnWhyQuickFix(),
        new ModifyCodeStyleQuickFix())
    }
  }

  private class AddTypeAnnotationQuickFix(element: PsiElement) extends AbstractFixOnPsiElement("Add type annotation", element) {
    def doApplyFix(project: Project): Unit = {
      val elem = getElement
      ToggleTypeAnnotation.complete(AddOnlyStrategy.withoutEditor, elem)(project.typeSystem)
    }
  }

  private class LearnWhyQuickFix extends LocalQuickFixBase("Learn Why...") {
    def applyFix(project: Project, problemDescriptor: ProblemDescriptor) {
      DesktopUtils.browse("http://blog.jetbrains.com/scala/2016/10/05/beyond-code-style/")
    }
  }

  private class ModifyCodeStyleQuickFix extends LocalQuickFixBase("Modify Code Style...") {
    def applyFix(project: Project, problemDescriptor: ProblemDescriptor): Unit = {
      TypeAnnotationUtil.showTypeAnnotationsSettings(project)
    }
  }
}
