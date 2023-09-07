package org.jetbrains.plugins.scalaDirective.codeInspection.dependencies

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scalaDirective.ScalaDirectiveBundle
import org.jetbrains.plugins.scalaDirective.dependencies.ScalaDirectiveDependencyDescriptor
import org.jetbrains.plugins.scalaDirective.util.ScalaDirectiveValueKind

final class ScalaDirectiveUpdateDependencyVersionQuickFix(element: PsiElement, newVersion: String)
  extends AbstractFixOnPsiElement(ScalaDirectiveBundle.message("packagesearch.update.dependency.to.newer.stable.version", newVersion), element) {
  override protected def doApplyFix(element: PsiElement)(implicit project: Project): Unit = element match {
    case ScalaDirectiveValueKind(ScalaDirectiveDependencyDescriptor(descriptor), kind) =>
      val dependencyText = ScalaDirectiveDependencyDescriptor.render(descriptor.copy(version = Some(newVersion)))
      val newElementText = kind.wrap(dependencyText)
      val newElement = ScalaPsiElementFactory.createDirectiveValueFromText(newElementText)
      element.replace(newElement)
    case _ =>
  }
}
