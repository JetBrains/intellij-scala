package org.jetbrains.plugins.scala.meta

import org.jetbrains.plugins.scala.base.{ScalaLightPlatformCodeInsightTestCaseAdapter, SimpleTestCase}
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

class TreeConverterTestBase extends SimpleTestCase with TreeConverterTestUtils {
  override def myProjectAdapter = fixture.getProject
  def testOk() = () // to get rid of no tests found spam in IDEA junit runner
}

class TreeConverterTestBaseWithLibrary extends ScalaLightPlatformCodeInsightTestCaseAdapter with TreeConverterTestUtils {
  override protected def getDefaultScalaSDKVersion: ScalaSdkVersion = ScalaSdkVersion._2_11
  override def myProjectAdapter = getProjectAdapter
  def testOk() = ()
}
