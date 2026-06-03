package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.util.GeneratedParameterizedTestFactory.SimpleTestData
import org.jetbrains.plugins.scala.util.{GeneratedHighlightingParameterizedTest, GeneratedParameterizedTestFactory}
import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

// SCL-21799
@Category(Array(classOf[TypecheckerTests]))
class Scala3CaseClassTest extends GeneratedHighlightingParameterizedTest(ScalaVersion.Latest.Scala_3_3) {
  override type TD = SimpleTestData

  override def testData: Seq[SimpleTestData] = Scala3CaseClassTest.testData
}

object Scala3CaseClassTest {
  lazy val testData: Seq[SimpleTestData] = Seq(
    """
      |// testUnapplyMethod0Param
      |case class A()
      |
      |object Test {
      |  val a: A = A()
      |
      |  val A() = a
      |  val _y: true = A.unapply(a)
      |}
      |""".stripMargin,
    """
      |// testUnapplyMethod0ParamWhenExtractorAccessorIsPresent
      |case class A() {
      |  def _1 = 1
      |}
      |
      |object Test {
      |  val a: A = A()
      |
      |  val A() = a
      |  val _y: true = A.unapply(a)
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
      |""".stripMargin,
    """
      |// caseClassWithTypeParameters
      |
      |case class A[T](t: T)
      |
      |def test[T](a: A[T]): Unit = {
      | val A(t) = a
      | val _t: T = t
      |
      | val _acc1: T = a._1
      |
      | val _z: A[T] = A.unapply(a)
      |}
      |""".stripMargin,
    """
      |// testAutoTupling
      |
      |case class A[T](t: (T, T))
      |
      |def test[T](a: A[T]): Unit = {
      | val A(t, _) = a
      | val _t: T = t
      |
      | val _acc1: (T, T) = a._1
      |
      | val _z: A[T] = A.unapply(a)
      |}
      |""".stripMargin,
    """
      |// testRepeatedParam
      |case class A[T](x: Int, t: T*)
      |
      |def test[T](a: A[T]): Unit = {
      |  {
      |    val _i: Int = a._1
      |    val _t: Seq[T] = a._2
      |  }
      |  {
      |    val A(i) = a
      |    val _i: Int = i
      |  }
      |  {
      |    val A(i, t1) = a
      |    val _i: Int = i
      |    val _t1: T = t1
      |  }
      |  {
      |    val A(i, t1, t2, tt*) = a
      |    val _i: Int = i
      |    val _t1: T = t1
      |    val _t2: T = t2
      |    val _tt: Seq[T] = tt
      |  }
      |}
      |""".stripMargin,
    """
      |// testPrivateConstructor
      |case class Wrapper private(x: String)
      |
      |object Wrapper {
      |  def apply(x: Int): Wrapper = null
      |}
      |
      |object Usage {
      |  println(Wrapper(1))
      |  println(Wrapper("Hello")) // Error
      |}
      |""".stripMargin,
    """
      |// testPrivateUnapply
      |case class Wrapper private(x: String)
      |
      |def test(w: Wrapper): Unit = {
      |  val Wrapper(s) = w // Allowed
      |}
      |""".stripMargin,
    """
      |// testPrivateCopy
      |case class Wrapper private(x: String)
      |
      |def test(w: Wrapper): Unit = {
      |  w.copy(x = "Hello") // Error
      |}
      |""".stripMargin,
    """
      |// testEmptyCopy
      |case class Wrapper()
      |
      |def test(w: Wrapper): Wrapper = {
      |  w.copy()
      |}
      |""".stripMargin
  ).map(GeneratedParameterizedTestFactory.testDataFromCode)
}
