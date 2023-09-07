package org.jetbrains.plugins.scalaDirective.lang.completion

import com.intellij.codeInsight.completion.{CompletionContributor, CompletionType}
import org.jetbrains.plugins.scalaDirective.dependencies.ScalaDirectiveDependencyPattern

final class ScalaDirectiveDependencyCompletionContributor extends CompletionContributor {
  extend(CompletionType.BASIC, ScalaDirectiveDependencyPattern, new ScalaDirectiveDependencyCompletionProvider)
}
