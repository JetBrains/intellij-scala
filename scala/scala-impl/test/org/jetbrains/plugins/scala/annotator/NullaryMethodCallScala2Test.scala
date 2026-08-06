package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

class NullaryMethodCallScala2Test extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion) = version == LatestScalaVersions.Scala_2

  def testSCL24626(): Unit = checkTextHasNoErrors(
    s"""
       |class MyScalaClass {
       |  override def toString: String = ???
       |}
       |
       |class Usage {
       |  val value: MyScalaClass = new MyScalaClass()
       |  value.toString
       |  value.toString()
       |}
       |""".stripMargin
  )

  def testSCL24626Parent(): Unit = checkTextHasNoErrors(
    s"""
       |trait Trt { def foo(): String = ??? }
       |class MyScalaClass extends Trt {
       |  override def foo: String = ???
       |}
       |
       |class Usage {
       |  val value: MyScalaClass = new MyScalaClass()
       |  value.foo
       |  value.foo()
       |}
       |""".stripMargin
  )
}

class NullaryMethodCallScala3Test extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3

  def testSCL24626(): Unit = checkHasErrorAroundCaret(
    s"""
       |class MyScalaClass {
       |  def foo(): String = ???
       |}
       |
       |class Usage {
       |  val value: MyScalaClass = new MyScalaClass()
       |  value.f${CARET}oo
       |}
       |""".stripMargin
  )

  def testSCL24626Java(): Unit = checkTextHasNoErrors(
    s"""
       |class MyScalaClass {
       |  override def toString(): String = ???
       |}
       |
       |class Usage {
       |  val value: MyScalaClass = new MyScalaClass()
       |  value.toString
       |}
       |""".stripMargin
  )
}


