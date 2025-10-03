package org.jetbrains.plugins.scala.mlCompletion.sbt

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.CaptureExt
import org.jetbrains.sbt.language.completion.SbtPsiElementPatterns.{sbtFilePattern, sbtModuleIdPattern, scalaFilePattern, versionPattern}

/** Disable ML Sorting for completion in `scalaVersion`/`libraryDependencies` versions in `.scala` and `.sbt` files */
private class SbtDependencyVersionInSbtAndScalaFilesCompletionMLPolicy extends SbtDependencyVersionCompletionMLPolicy {
  override protected def VERSION_PATTERN: ElementPattern[? <: PsiElement] =
    (sbtFilePattern || scalaFilePattern) && psiElement.inside(versionPattern || sbtModuleIdPattern)
}
