package org.jetbrains.plugins.scala.meta

import org.jetbrains.plugins.scala.base.{ScalaLightPlatformCodeInsightTestCaseAdapter, SimpleTestCase}
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

class TreeConverterTestBase extends SimpleTestCase with TreeConverterTestUtils {
  override def myProjectAdapter = fixture.getProject
}

class TreeConverterTestBaseWithLibrary extends ScalaLightPlatformCodeInsightTestCaseAdapter with TreeConverterTestUtils {
  override protected def getDefaultScalaSDKVersion: ScalaSdkVersion = ScalaSdkVersion._2_11

  override def myProjectAdapter = getProjectAdapter
}
