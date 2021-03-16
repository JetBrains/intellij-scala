package org.jetbrains.plugins.scala.mlCompletion

import com.intellij.internal.ml.catboost.CatBoostJarCompletionModelProvider
import com.intellij.lang.Language
import org.jetbrains.plugins.scala.ScalaLanguage

class ScalaMlRankingProvider extends CatBoostJarCompletionModelProvider("Scala", "scala_features", "scala_model") {
  override def isLanguageSupported(language: Language): Boolean = language == ScalaLanguage.INSTANCE

  override def isEnabledByDefault: Boolean = true
}
