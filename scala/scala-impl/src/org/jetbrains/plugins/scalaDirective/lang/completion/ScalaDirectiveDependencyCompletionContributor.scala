package org.jetbrains.plugins.scalaDirective.lang.completion

import com.intellij.codeInsight.completion.{CompletionContributor, CompletionType}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scalaDirective.dependencies.ScalaDirectiveDependencyPattern

final class ScalaDirectiveDependencyCompletionContributor extends CompletionContributor with DumbAware {
  extend(CompletionType.BASIC, ScalaDirectiveDependencyPattern, new ScalaDirectiveDependencyCompletionProvider)
}
