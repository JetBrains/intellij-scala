package org.jetbrains.plugins.scalaDirective.lang.completion

import com.intellij.codeInsight.completion.{CompletionContributor, CompletionType}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scalaDirective.dependencies.ScalaDirectiveDependencyPattern

abstract class ScalaDirectiveDependencyCompletionContributorBase extends CompletionContributor with DumbAware {
  protected def provider: ScalaDirectiveDependencyCompletionProviderBase

  extend(CompletionType.BASIC, ScalaDirectiveDependencyPattern, provider)
}
