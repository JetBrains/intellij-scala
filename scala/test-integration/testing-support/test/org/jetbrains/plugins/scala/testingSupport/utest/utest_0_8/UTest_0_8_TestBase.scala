package org.jetbrains.plugins.scala.testingSupport.utest.utest_0_8

import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.testingSupport.utest.UTestTestCase

abstract class UTest_0_8_TestBase extends UTestTestCase {
  override def uTestVersion: Version = UTestTestCase.LatestVersions.UTest_0_8
}

