package org.jetbrains.plugins.scala.testingSupport.utest.scala2_12

import org.jetbrains.plugins.scala.testingSupport.utest.UTestTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

abstract class UTestTestBase_2_12 extends UTestTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12

  override protected val testSuiteSecondPrefix = ""
}
