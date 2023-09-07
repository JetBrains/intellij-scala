package org.jetbrains.plugins.scalaDirective.codeInspection.dependencies

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.packagesearch.codeInspection.DependencyVersionInspection
import org.jetbrains.plugins.scala.packagesearch.codeInspection.DependencyVersionInspection.DependencyDescriptor
import org.jetbrains.plugins.scalaDirective.dependencies.{ScalaDirectiveDependencyDescriptor, ScalaDirectiveDependencyPattern}
import org.jetbrains.plugins.scalaDirective.util.ScalaDirectiveValueKind

final class ScalaDirectiveDependencyVersionInspection extends DependencyVersionInspection {
  override protected def isAvailable(element: PsiElement): Boolean =
    ScalaDirectiveDependencyPattern.accepts(element)

  override protected def createDependencyDescriptor(element: PsiElement): Option[DependencyDescriptor] = element match {
    case ScalaDirectiveValueKind(ScalaDirectiveDependencyDescriptor(descriptor), _) => Some(descriptor)
    case _ => None
  }

  override protected def createQuickFix(element: PsiElement, newerVersion: String): LocalQuickFix =
    new ScalaDirectiveUpdateDependencyVersionQuickFix(element, newerVersion)
}
