package org.jetbrains.plugins.scala.testingSupport.utest.scala2_13

import org.jetbrains.plugins.scala.testingSupport.utest.UTestTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

abstract class UTestTestBase_2_13 extends UTestTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13

  override protected val testSuiteSecondPrefix = ""
}
