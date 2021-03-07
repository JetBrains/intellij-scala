package org.jetbrains.plugins.scala.mlCompletion

import com.intellij.completion.ml.features.CompletionFeaturesPolicy

class ScalaCompletionFeaturesPolicy extends CompletionFeaturesPolicy {
  override def useNgramModel(): Boolean = true
}
