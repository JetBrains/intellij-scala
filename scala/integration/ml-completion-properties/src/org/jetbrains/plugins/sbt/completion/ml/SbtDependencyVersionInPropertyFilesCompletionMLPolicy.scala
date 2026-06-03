package org.jetbrains.plugins.sbt.completion.ml

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.CaptureExt
import org.jetbrains.plugins.scala.mlCompletion.sbt.SbtDependencyVersionCompletionMLPolicy
import org.jetbrains.sbt.language.completion.SbtPropertiesPsiElementPatterns.{propertiesFilePattern, versionPropertyPattern}

/**
 * Disable ML Sorting for completion in `sbt.version` in `.properties` files.
 *
 * WARNING: Uses classes defined in properties plugin.
 * Only register in config files with optional dependencies on the properties plugin
 */
private class SbtDependencyVersionInPropertyFilesCompletionMLPolicy extends SbtDependencyVersionCompletionMLPolicy {
  override protected def VERSION_PATTERN: ElementPattern[? <: PsiElement] =
    propertiesFilePattern && psiElement.inside(versionPropertyPattern)
}
