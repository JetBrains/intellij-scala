package org.jetbrains.plugins.scala.testingSupport.utest.utest_0_9.scala2_13

import org.jetbrains.plugins.scala.testingSupport.utest.utest_0_9.UTest_0_9_TestBase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

abstract class UTest_0_9_Scala_2_13_TestBase extends UTest_0_9_TestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13
}
