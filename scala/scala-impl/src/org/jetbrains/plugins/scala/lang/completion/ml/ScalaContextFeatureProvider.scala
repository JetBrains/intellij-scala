package org.jetbrains.plugins.scala
package lang
package completion
package ml

import java.util

import com.intellij.codeInsight.completion.ml.{CompletionEnvironment, ContextFeatureProvider, MLFeatureValue}

final class ScalaContextFeatureProvider extends ContextFeatureProvider {

  override def getName: String = ScalaLowerCase

  override def calculateFeatures(environment: CompletionEnvironment): util.Map[String, MLFeatureValue] = {
    val position = Position.getValue(environment)

    val features = new util.HashMap[String, MLFeatureValue]()

    features.put("location", MLFeatureValue.categorical(location(position)))
    features.put("previous_keyword", MLFeatureValue.categorical(previousKeyword(position)))

    features
  }
}
