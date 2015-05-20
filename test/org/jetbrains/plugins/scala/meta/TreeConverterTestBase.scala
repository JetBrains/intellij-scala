package org.jetbrains.plugins.scala.meta

import org.jetbrains.plugins.scala.base.{ScalaLightPlatformCodeInsightTestCaseAdapter, SimpleTestCase}

class TreeConverterTestBase extends SimpleTestCase with TreeConverterTestUtils {
  override def myProjectAdapter = fixture.getProject
}

class TreeConverterTestBaseWithLibrary extends ScalaLightPlatformCodeInsightTestCaseAdapter with TreeConverterTestUtils {
  override def myProjectAdapter = getProjectAdapter
}
