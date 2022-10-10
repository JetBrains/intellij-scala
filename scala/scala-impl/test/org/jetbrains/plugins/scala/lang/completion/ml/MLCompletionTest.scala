package org.jetbrains.plugins.scala.lang.completion.ml

import org.jetbrains.plugins.scala.base.{HelperFixtureEditorOps, ScalaLightCodeInsightFixtureTestCase, SharedTestProjectToken}

abstract class MLCompletionTest extends ScalaLightCodeInsightFixtureTestCase with HelperFixtureEditorOps {
  override protected def sharedProjectToken = SharedTestProjectToken.ByScalaSdkAndProjectLibraries(this)
}
