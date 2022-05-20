package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class SingletonConformanceTest extends ScalaLightCodeInsightFixtureTestAdapter {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_13

  def testSimpleConformance(): Unit = checkTextHasNoErrors(
    """
      |object Obj {
      |  val a: Int = 123
      |  val s: Singleton = 123
      |  val s1: Singleton = a
      |  val s2: Singleton = Obj
      |  val s3: Singleton = this
      |}
      |""".stripMargin
  )

  def testSCL17449Pos(): Unit = checkTextHasNoErrors(
    """
      |trait Parent {
      |  self: Singleton =>
      |  def fun(): Unit
      |}
      |object Child extends Parent {
      |  override def fun(): Unit = print("Hello Idea")
      |}
      |""".stripMargin
  )

  def testSCL1744Neg(): Unit = checkHasErrorAroundCaret(
    s"""
       |trait Parent {
       |  self: Singleton =>
       |  def fun(): Unit
       |}
       |class Child extends ${CARET}Parent {
       |  override def fun(): Unit = print("Hello Idea")
       |}
       |""".stripMargin
  )
}
