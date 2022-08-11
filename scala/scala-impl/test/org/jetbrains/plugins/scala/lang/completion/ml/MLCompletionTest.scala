package org.jetbrains.plugins.scala.lang.completion.ml

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.{HelperFixtureEditorOps, ScalaLightCodeInsightFixtureTestCase, SharedTestProjectToken}

abstract class MLCompletionTest extends ScalaLightCodeInsightFixtureTestCase with HelperFixtureEditorOps {
  override protected def sharedProjectToken = SharedTestProjectToken.ByScalaSdkAndProjectLibraries(this)

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_2_13
}
