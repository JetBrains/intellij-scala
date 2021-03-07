package org.jetbrains.plugins.scala.mlCompletion

import com.intellij.internal.ml.catboost.CatBoostJarCompletionModelProvider
import com.intellij.lang.Language
import org.jetbrains.plugins.scala.ScalaLanguage

class ScalaMlRankingProvider extends CatBoostJarCompletionModelProvider("Scala", "scala_features_exp", "scala_model_exp") {
  override def isLanguageSupported(language: Language): Boolean = language == ScalaLanguage.INSTANCE
}
