package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class SingletonConformanceTest extends ScalaLightCodeInsightFixtureTestCase {
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

class SingletonConformance_3 extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3

  def testSCL24794(): Unit = checkTextHasNoErrors(
    """
      |type T
      |
      |object Outside {
      |  def method(): Unit = {
      |    val v: T = ???
      |    val t: T = (??? : v.type)
      |  }
      |}
      |""".stripMargin
  )

  def testSingletonBoundedExpectedType(): Unit = checkTextHasNoErrors(
    """object Test {
      |  def f1(x: AnyRef with Singleton): AnyRef with Singleton = x
      |  def narrow1(x: AnyRef): AnyRef with Singleton = x
      |  def narrow2[T <: AnyRef](x: T): AnyRef & Singleton = x
      |}
      |
      |class A {
      |  val x: Singleton with this.type = this
      |  val y: this.type = x
      |}
      |
      |class B {
      |  val x = ""
      |  val xs: x.type with Singleton = x
      |  val y: x.type = xs
      |}
      |""".stripMargin
  )
}
