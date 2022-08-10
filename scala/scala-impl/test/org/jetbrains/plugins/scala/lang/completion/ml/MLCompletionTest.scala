package org.jetbrains.plugins.scala.lang.completion.ml

import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestAdapter, SharedTestProjectToken}

abstract class MLCompletionTest extends ScalaLightCodeInsightFixtureTestAdapter {
  override protected def sharedProjectToken = SharedTestProjectToken.ByScalaSdkAndProjectLibraries(this)
}
