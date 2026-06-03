package org.jetbrains.plugins.scala.lang.completion.ml

import com.intellij.codeInsight.completion.ml.{CompletionEnvironment, ContextFeatureProvider, MLFeatureValue}
import org.jetbrains.plugins.scala.ScalaLowerCase

import java.util

final class ScalaContextFeatureProvider extends ContextFeatureProvider {

  override def getName: String = ScalaLowerCase

  override def calculateFeatures(environment: CompletionEnvironment): util.Map[String, MLFeatureValue] = {
    val position = environment.getParameters.getPosition

    val features = new util.HashMap[String, MLFeatureValue]()

    features.put("location", MLFeatureValue.categorical(location(position)))
    features.put("previous_keyword", MLFeatureValue.categorical(previousKeyword(position)))

    features
  }
}
