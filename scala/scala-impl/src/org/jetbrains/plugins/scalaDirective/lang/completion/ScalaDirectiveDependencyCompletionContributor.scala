package org.jetbrains.plugins.scalaDirective.lang.completion

import com.intellij.codeInsight.completion.{CompletionContributor, CompletionType}
import org.jetbrains.plugins.scalaDirective.dependencies.ScalaDirectiveDependencyPattern

// TODO(SCL-21496): auto popup on ':' in scala directive dependency
final class ScalaDirectiveDependencyCompletionContributor extends CompletionContributor {
  extend(CompletionType.BASIC, ScalaDirectiveDependencyPattern, new ScalaDirectiveDependencyCompletionProvider)
}
