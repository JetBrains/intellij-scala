//noinspection ApiStatus,UnstableApiUsage
package org.jetbrains.plugins.sbt.completion.ml

import com.intellij.codeInsight.completion.{CompletionParameters, CompletionType}
import com.intellij.completion.ml.CompletionMLPolicy
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.CaptureExt
import org.jetbrains.sbt.language.completion.SbtPsiElementPatterns._

// TODO: Reimplement when https://youtrack.jetbrains.com/issue/IDEA-272935 is fixed
private abstract class SbtDependencyVersionCompletionMLPolicy extends CompletionMLPolicy {
  protected def VERSION_PATTERN: ElementPattern[_ <: PsiElement]

  override def isReRankingDisabled(params: CompletionParameters): Boolean =
    params.getCompletionType == CompletionType.BASIC && VERSION_PATTERN.accepts(params.getPosition)
}

/** Disable ML Sorting for completion in `scalaVersion`/`libraryDependencies` versions in `.scala` and `.sbt` files */
private class SbtDependencyVersionInSbtAndScalaFilesCompletionMLPolicy extends SbtDependencyVersionCompletionMLPolicy {
  override protected def VERSION_PATTERN: ElementPattern[_ <: PsiElement] =
    (sbtFilePattern || scalaFilePattern) && psiElement.inside(versionPattern || sbtModuleIdPattern)
}

/**
 * Disable ML Sorting for completion in `sbt.version` in `.properties` files.
 *
 * WARNING: Uses classes defined in properties plugin.
 * Only register in config files with optional dependencies on the properties plugin
 */
private class SbtDependencyVersionInPropertyFilesCompletionMLPolicy extends SbtDependencyVersionCompletionMLPolicy {
  override protected def VERSION_PATTERN: ElementPattern[_ <: PsiElement] =
    propertiesFilePattern && psiElement.inside(versionPropertyPattern)
}
