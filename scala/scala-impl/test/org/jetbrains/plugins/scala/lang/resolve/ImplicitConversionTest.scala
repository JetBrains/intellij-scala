package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.failed.resolve.FailableResolveTest
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ImplicitConversionTest extends FailableResolveTest("implicitConversion") {
  override protected def shouldPass = true

  def testSCL10447(): Unit = doTest()

  def testSCL12098(): Unit = doTest()

  def testSCL13306(): Unit = doTest()

  def testSCL13859(): Unit = doTest()
}

class ImplicitConversionTest_since_2_11 extends FailableResolveTest("implicitConversion") {
  override protected def shouldPass = true

  // uses scala.collection.Searching, which is available since Scala 2.11
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_11

  def testSCL10299(): Unit = doTest()
}
