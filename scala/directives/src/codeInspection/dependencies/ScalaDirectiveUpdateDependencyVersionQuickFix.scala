//package org.jetbrains.plugins.scalaDirective.codeInspection.dependencies
//
//import com.intellij.openapi.project.Project
//import com.intellij.psi.PsiElement
//import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
//import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
//import org.jetbrains.plugins.scalaDirective.ScalaDirectiveBundle
//import org.jetbrains.plugins.scalaDirective.codeInspection.dependencies.ScalaDirectiveDependencyVersionInspection.{ScalaDirectiveDependencyDescriptor, getDependencyDescriptor}
//
//final class ScalaDirectiveUpdateDependencyVersionQuickFix(element: PsiElement, newVersion: String)
//  extends AbstractFixOnPsiElement(ScalaDirectiveBundle.message("packagesearch.update.dependency.to.newer.stable.version", newVersion), element) {
//  override protected def doApplyFix(element: PsiElement)(implicit project: Project): Unit =
//    getDependencyDescriptor(element).foreach { descriptor =>
//      val dependencyText = ScalaDirectiveDependencyDescriptor.render(descriptor.copy(version = newVersion))
//      val newElement = ScalaPsiElementFactory.createDirectiveValueFromText(dependencyText)
//      element.replace(newElement)
//    }
//}
