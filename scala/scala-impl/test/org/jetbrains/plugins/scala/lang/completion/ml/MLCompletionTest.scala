package org.jetbrains.plugins.scala.lang.completion.ml

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.{HelperFixtureEditorOps, ScalaLightCodeInsightFixtureTestCase}

abstract class MLCompletionTest extends ScalaLightCodeInsightFixtureTestCase with HelperFixtureEditorOps {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_2_13
}
