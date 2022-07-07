//noinspection ApiStatus,UnstableApiUsage
package org.jetbrains.plugins.sbt.completion.ml

import com.intellij.codeInsight.completion.{CompletionParameters, CompletionType}
import com.intellij.completion.ml.CompletionMLPolicy
import com.intellij.patterns.PlatformPatterns.psiElement
import org.jetbrains.plugins.sbt.completion.ml.SbtDependencyVersionCompletionMLPolicy.VERSION_PATTERN
import org.jetbrains.plugins.scala.lang.completion.CaptureExt
import org.jetbrains.sbt.language.completion.SbtPsiElementPatterns._

// TODO: Reimplement when https://youtrack.jetbrains.com/issue/IDEA-272935 is fixed
/** Disable ML Sorting for completion in:
 *  - `scalaVersion`/`libraryDependencies` versions in `.scala` and `.sbt` files
 *  - `sbt.version` in `.properties` files
 */
class SbtDependencyVersionCompletionMLPolicy extends CompletionMLPolicy {
  override def isReRankingDisabled(params: CompletionParameters): Boolean =
    params.getCompletionType == CompletionType.BASIC && VERSION_PATTERN.accepts(params.getPosition)
}

object SbtDependencyVersionCompletionMLPolicy {
  private[this] val DEPENDENCY_VERSION_IN_SBT_OR_SCALA_PATTERN =
    (sbtFilePattern || scalaFilePattern) && psiElement.inside(versionPattern || sbtModuleIdPattern)

  private[this] val SBT_VERSION_IN_PROPERTIES_PATTERN = propertiesFilePattern && psiElement.inside(versionPattern)

  private val VERSION_PATTERN = DEPENDENCY_VERSION_IN_SBT_OR_SCALA_PATTERN || SBT_VERSION_IN_PROPERTIES_PATTERN
}
