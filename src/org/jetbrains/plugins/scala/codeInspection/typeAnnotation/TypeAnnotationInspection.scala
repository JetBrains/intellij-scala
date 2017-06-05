package org.jetbrains.plugins.scala
package codeInspection.typeAnnotation

import java.util

import com.intellij.codeInspection._
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.intention.types.{AddOnlyStrategy, ToggleTypeAnnotation, UpdateStrategy}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings._
import org.jetbrains.plugins.scala.lang.psi.TypeAdjuster
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.util.TypeAnnotationUtil

import scala.collection.mutable.ArrayBuffer

/**
 * Pavel Fatin
 */
class TypeAnnotationInspection extends AbstractInspection {
  // TODO Treat "simple" expressions just like any other expressions
  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
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

    case method: ScFunctionDefinition if method.hasAssign && !method.hasExplicitType && !method.isSecondaryConstructor &&
      !method.hasAnnotation("org.junit.Test") && !method.hasAnnotation("junit.framework.Test") &&
      !TypeAnnotationUtil.isMemberOf(method, "junit.framework.TestCase") =>

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

  private class AddTypeAnnotationQuickFix(element: PsiElement)
    extends AbstractFixOnPsiElement("Add type annotation", element) with BatchQuickFix[CommonProblemDescriptor] {

    def doApplyFix(project: Project): Unit = {
      val elem = getElement
      ToggleTypeAnnotation.complete(AddOnlyStrategy.withoutEditor, elem)
    }

    override def applyFix(project: Project,
                          descriptors: Array[CommonProblemDescriptor],
                          psiElementsToIgnore: util.List[PsiElement],
                          refreshViews: Runnable): Unit = {

      val fixes = descriptors.flatMap { d =>
        d.getFixes.collect {
          case addTypeFix: AddTypeAnnotationQuickFix => addTypeFix
        }
      }
      val buffer = ArrayBuffer[(ScTypeElement, PsiElement)]()
      val strategy = new CollectTypesToAddStrategy(buffer)

      val computeTypesRunnable = new Runnable {
        override def run(): Unit = inReadAction {
          for (fix <- fixes) {
            val elem = fix.getElement
            ToggleTypeAnnotation.complete(strategy, elem)
            val progress = ProgressManager.getInstance().getProgressIndicator
            progress.setFraction(progress.getFraction + 1.0 / fixes.length)
            progress.setText(elem.getText)
          }
        }
      }

      ApplicationManagerEx.getApplicationEx
        .runProcessWithProgressSynchronously(computeTypesRunnable, getFamilyName, true, project)

      inWriteCommandAction(project, getFamilyName) {
        buffer.foreach {
          case (typeElem, anchor) =>
            val added = strategy.addActualType(typeElem, anchor)
            TypeAdjuster.markToAdjust(added)

            Option(refreshViews).foreach(_.run())
        }
      }
    }

    private class CollectTypesToAddStrategy(buffer: ArrayBuffer[(ScTypeElement, PsiElement)]) extends AddOnlyStrategy(editor = None) {

      override def addTypeAnnotation(t: ScType, context: PsiElement, anchor: PsiElement): Unit = {
        val tps = UpdateStrategy.annotationsFor(t, context)
        buffer += ((tps.head, anchor))
      }
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
