package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

// SCL-21799
@Category(Array(classOf[TypecheckerTests]))
class Scala3CaseClassTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testUnapplyMethod0Param(): Unit = checkTextHasNoErrors(
    """
      |case class A()
      |
      |object Test {
      |  val a: A = A(123)
      |
      |  val A() = a
      |  val _y: Boolean = A.unapply(a)
      |}
      |""".stripMargin
  )

  def testUnapplyMethod1Param(): Unit = checkTextHasNoErrors(
    """
      |case class A(i: Int)
      |
      |object Test {
      |  val a: A = A(123)
      |
      |  val A(i) = a
      |  val _x: Int = i
      |
      |  val _y: A = A.unapply(a)
      |}
      |""".stripMargin
  )

  def testUnapplyMethod2Param(): Unit = checkTextHasNoErrors(
    """
      |case class A(i: Int, s: String)
      |
      |object Test {
      |  val a: A = A(123, "test")
      |
      |  val A(i, s) = a
      |  val _x: Int = i
      |  val _y: String = s
      |
      |  val _y: A = A.unapply(a)
      |}
      |""".stripMargin
  )

  def testAccessor(): Unit = checkTextHasNoErrors(
    """
      |case class A(i: Int, s: String)
      |
      |object Test {
      |  val a: A = A(123, "test")
      |
      |  val _x: Int = a._1
      |  val _y: String = a._2
      |}
      |""".stripMargin
  )
}
