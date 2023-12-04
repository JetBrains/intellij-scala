package org.jetbrains.plugins.scala.lang.typeInference

import junit.framework.TestCase
import org.jetbrains.plugins.scala.util.GeneratedTestSuiteFactory
import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

// SCL-21799
@Category(Array(classOf[TypecheckerTests]))
class Scala3CaseClassTest extends TestCase

object Scala3CaseClassTest extends GeneratedTestSuiteFactory.withHighlightingTest(ScalaVersion.Latest.Scala_3_3) {
  lazy val testData: Seq[TestData] = Seq(
    """
      |// testUnapplyMethod0Param
      |case class A()
      |
      |object Test {
      |  val a: A = A()
      |
      |  val A() = a
      |  val _y: Boolean = A.unapply(a)
      |}
      |""".stripMargin,
    """
      |// testUnapplyMethod1Param
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
      |""".stripMargin,
    """
      |// testUnapplyMethod2Param
      |case class A(i: Int, s: String)
      |
      |object Test {
      |  val a: A = A(123, "test")
      |
      |  val A(i, s) = a
      |  val _x: Int = i
      |  val _y: String = s
      |
      |  val _z: A = A.unapply(a)
      |}
      |""".stripMargin,
    """
      |// testAccessor
      |case class A(i: Int, s: String)
      |
      |object Test {
      |  val a: A = A(123, "test")
      |
      |  val _x: Int = a._1
      |  val _y: String = a._2
      |}
      |
      |""".stripMargin
  ).map(testDataFromCode)
}
