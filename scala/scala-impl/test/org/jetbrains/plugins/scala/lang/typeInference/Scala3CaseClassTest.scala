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
      |  val _acc1: Int = a._1
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
      |  val _acc1: Int = a._1
      |  val _acc2: String = a._2
      |
      |  val _z: A = A.unapply(a)
      |}
      |""".stripMargin,
    """
      |// testAlreadyDefinedUnapply
      |case class A(i: Int, s: String)
      |object A {
      |  def unapply(a: A): Some[(Double, Double)] = Some((1.0, 1.0))
      |}
      |
      |object Test {
      |  val a: A = A(123, "test")
      |
      |  val A(d1, d2) = a
      |  val _d1: Double = d1
      |  val _d2: Double = d2
      |
      |  val _acc1: Int = a._1
      |  val _acc2: String = a._2
      |
      |  val Some((e1, e2)) = A.unapply(a)
      |  val _e1: Double = e1
      |  val _e2: Double = e2
      |}
      |""".stripMargin,
    """
      |// testAlreadyDefinedAccessors
      |case class A(i: Int, s: String) {
      |  def _1: Boolean = true
      |  def _3: Boolean = false
      |}
      |
      |object Test {
      |  val a: A = A(123, "test")
      |
      |  val A(b1, s, b2) = a
      |  val _b1: Boolean = b1
      |  val _s: String = s
      |  val _b2: Boolean = b2
      |
      |  val _acc1: Boolean = a._1
      |  val _acc2: String = a._2
      |  val _acc3: Boolean = a._3
      |
      |  val _z: A = A.unapply(a)
      |}
      |""".stripMargin,
    """
      |// testAlreadyDefinedOneAccessor
      |case class A(i: Int) {
      |  def _1: Boolean = true
      |}
      |
      |object Test {
      |  val a: A = A(123)
      |
      |  val A(b) = a
      |  val _b: Boolean = b
      |
      |  val _acc1: Boolean = a._1
      |
      |  val _z: A = A.unapply(a)
      |}
      |""".stripMargin,
    """
      |// testTupleMember
      |case class A(i: (Int, Int))
      |
      |object Test {
      |  val a: A = A((123, 321))
      |
      |  val A(t) = a
      |  val _t: (Int, Int) = t
      |
      |  val _acc1: (Int, Int) = a._1
      |
      |  val _z: A = A.unapply(a)
      |}
      |""".stripMargin,
    """
      |// testOption
      |val Some(x: Int) = Option(1)
      |val _o: Option[Int] = Some.unapply(Some(1))
      |""".stripMargin
  ).map(testDataFromCode)
}
