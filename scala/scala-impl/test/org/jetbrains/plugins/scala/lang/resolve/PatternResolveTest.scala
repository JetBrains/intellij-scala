package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase._

class PatternResolveTest extends SimpleResolveTestBase {
  override protected def supportedIn(version: ScalaVersion) =
    version == LatestScalaVersions.Scala_2_12

  def testSCL13150(): Unit = {
    doResolveTest(
      s"""
        |object X {
        |  def f(x : Any): Any = x match { case ${REFTGT}A@_ => ${REFSRC}A }
        |}
      """.stripMargin)
  }

}
