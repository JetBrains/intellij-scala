package org.jetbrains.plugins.scala.testingSupport.utest.utest_0_9

import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.testingSupport.utest.UTestTestCase

abstract class UTest_0_9_TestBase extends UTestTestCase {
  override def uTestVersion: Version = UTestTestCase.LatestVersions.UTest_0_9
}

