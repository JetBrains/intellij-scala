package org.jetbrains.plugins.scala.lang.typeInference

import junit.framework.TestCase
import org.jetbrains.plugins.scala.util.GeneratedTestSuiteFactory
import org.jetbrains.plugins.scala.util.GeneratedTestSuiteFactory.SimpleTestData
import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

// SCL-21799
@Category(Array(classOf[TypecheckerTests]))
class Scala3UnapplyTest extends TestCase

object Scala3UnapplyTest extends GeneratedTestSuiteFactory.withHighlightingTest(ScalaVersion.Latest.Scala_3_3) {
  // See https://docs.scala-lang.org/scala3/reference/changed-features/pattern-matching.html#
  lazy val testData: Seq[SimpleTestData] = Seq(
    // ============= Boolean Match =============
    """
      |// booleanExtractor
      |object A:
      |  def unapply(i: Int): Boolean = true
      |object B:
      |  def unapply(i: Int): true = true
      |
      |val A() = 1
      |val B() = 2
      |""".stripMargin,
    """
      |// booleanExtractorForTypeParam
      |class A[T](a: T):
      |  def unapply(i: Int): T = a
      |
      |object a extends A(true)
      |
      |val a() = 1
      |""".stripMargin,
    """
      |// booleanExtractorForUnapplyWithTypeParam
      |object A:
      |  def unapply[T](t: T): T = t
      |
      |val A() = true
      |""".stripMargin,
    // ============= Product Match =============
    """
      |// productMatch1
      |class Result(val _1 : Int) extends Product1[Int]:
      | override def canEqual(that: Any): Boolean = true
      |
      |object A:
      |  def unapply(i: Int): Result = new Result(i)
      |
      |val A(i) = 1
      |val _i: Int = i
      |""".stripMargin,
    """
      |// productMatch2
      |class Result(val _1 : Int, val _2 : Boolean) extends Product2[Int, Boolean]:
      | override def canEqual(that: Any): Boolean = true
      |
      |object A:
      |  def unapply(i: Int): Result = new Result(i, true)
      |
      |val A(i, b) = 1
      |val _i: Int = i
      |val _b: Boolean = b
      |""".stripMargin,
    """
      |//productMatchWithTypeParam
      |
      |class Result[T](val _1 : T) extends Product1[T]:
      | override def canEqual(that: Any): Boolean = true
      |
      |object A:
      |  def unapply[T](t: T): Result[T] = new Result(t)
      |
      |val A(i) = 1
      |val _i: Int = i
      |""".stripMargin,
    """
      |//productMatchAutoTupling
      |
      |class Result[T](val _1 : T) extends Product1[T]:
      | override def canEqual(that: Any): Boolean = true
      |
      |object A:
      |  def unapply[T](t: T): Result[T] = new Result(t)
      |
      |val A(i, b) = (1, true)
      |val _i: Int = i
      |val _b: Boolean = b
      |
      |val A(t) = (1, true)
      |val _t: (Int, Boolean) = t
      |""".stripMargin,
    """
      |//productMatchNoAutoTupling
      |
      |class Result extends Product2[(Int, Boolean), String]:
      |  override def canEqual(that: Any): Boolean = true
      |  def _1 = (1, true)
      |  def _2 = "s"
      |
      |object A:
      |  def unapply(i: Int): Result = new Result
      |
      |val A(t, s) = 1
      |val _t: (Int, Boolean) = t
      |val _s: String = s
      |""".stripMargin,
    // ============= Single Match =============
    """
      |// singleMatch
      |class Result(val int: Int) {
      |  def isEmpty: Boolean = false
      |  def get: Boolean = true
      |}
      |
      |object A:
      |  def unapply(i: Int): Result = new Result(i)
      |
      |val A(b) = 1
      |
      |val _b: Boolean = b
      |""".stripMargin,
    """
      |// productMatchBeforeSingleMatch
      |class Result(val _1 : Int) extends Product1[Int] {
      |  override def canEqual(that: Any): Boolean = true
      |  def isEmpty: Boolean = false
      |  def get: Boolean = true
      |}
      |
      |object A:
      |  def unapply(i: Int): Result = new Result(i)
      |
      |val A(i) = 1
      |
      |val _i: Int = i
      |""".stripMargin,
    """
      |// productMatchOrSingleMatch
      |class Result(val _1 : Int) extends Product1[Int] {  // <- note that this is Product1 but has _1 and _2
      |  val _2: String = "blub"
      |  override def canEqual(that: Any): Boolean = true
      |  def isEmpty: Boolean = false
      |  def get: Boolean = true
      |}
      |
      |object A:
      |  def unapply(i: Int): Result = new Result(i)
      |
      |val A(b) = 1
      |val _b: Boolean = b
      |
      |val A(i, s) = 1
      |val _i: Int = i
      |val _s: String = s
      |""".stripMargin,
    """
      |// singleMatchIfNotInheritingProduct
      |class Result(val _1 : Int) {
      |  def isEmpty: Boolean = false
      |  def get: Boolean = true
      |}
      |
      |object A:
      |  def unapply(i: Int): Result = new Result(i)
      |
      |val A(b) = 1
      |
      |val _b: Boolean = b
      |""".stripMargin,
    """
      |// singleMatchWithTypeParameter
      |class Result[T](val get : T):
      |  def isEmpty: Boolean = false
      |
      |object A:
      |  def unapply[T](t: T): Result[T] = new Result(t)
      |
      |val A(s) = "s"
      |
      |val _s: String = s
      |""".stripMargin,
    """
      |// singleMatchAutoTupling
      |class Result[T](val get : T):
      |  def isEmpty: Boolean = false
      |
      |object A:
      |  def unapply[T](t: T): Result[T] = new Result(t)
      |
      |val A(i, b) = (1, true)
      |
      |val _i: Int = i
      |val _b: Boolean = b
      |
      |val A(t) = (1, true)
      |
      |val _t: (Int, Boolean) = t
      |""".stripMargin,
    // ============= name-based Match =============
    """
      |// nameBasedMatch2
      |class Result(val _1 : Int) {
      |  def _2: Float = 2.0f
      |}
      |
      |class Unapplied:
      |  def isEmpty = false
      |  def get = new Result(1)
      |
      |object A:
      |  def unapply(i: Int): Unapplied = new Unapplied
      |
      |val A(i, f) = 1
      |
      |val _i: Int = i
      |val _f: Float = f
      |""".stripMargin,
    """
      |// singleMatchBeforeNameBasedMatch
      |class Result(val _1 : Int)
      |
      |class Unapplied:
      |  def isEmpty = false
      |  def get = new Result(1)
      |
      |object A:
      |  def unapply(i: Int): Unapplied = new Unapplied
      |
      |val A(r) = 1
      |val _r: Result = r
      |""".stripMargin,
    """
      |// singleMatchBeforeNameBasedMatch2
      |class Result(val _1 : Int):
      |  def _2: Float = 2.0f
      |
      |class Unapplied:
      |  def isEmpty = false
      |  def get = new Result(1)
      |
      |object A:
      |  def unapply(i: Int): Unapplied = new Unapplied
      |
      |val A(r) = 1
      |val _r: Result = r
      |
      |val A(i, f) = 1
      |val _i: Int = i
      |val _f: Float = f
      |""".stripMargin,
    // no auto tupling in nameBasedMatching
    """
      |//nameBasedMatchNoAutoTupling
      |class Result[T](val _1 : T)
      |
      |class Unapplied[T](val get: Result[T]):
      |  def isEmpty = false
      |
      |object A:
      |  def unapply[T](t: T): Unapplied[T] = new Unapplied(new Result(t))
      |
      |
      |val A(s, i) = ("s", 1)   // Error
      |""".stripMargin,
    """
      |//nameBasedMatchNoAutoTupling2
      |class Result[T](val _1 : T):
      |  def _2: Boolean = true
      |
      |class Unapplied[T](val get: Result[T]):
      |  def isEmpty = false
      |
      |object A:
      |  def unapply[T](t: T): Unapplied[T] = new Unapplied(new Result(t))
      |
      |
      |val A(t, b) = ("s", 1)
      |
      |val _t: (String, Int) = t
      |val _b: Boolean = b
      |
      |""".stripMargin,
    """
      |// nameBasedOrSingleMatchOrProductMatch
      |class Result:
      |  def _1: Boolean = true
      |  def _2: Int = 1
      |  def _3: String = "s"
      |
      |class Unapplied extends Product2[String, Int]:
      |  def canEqual(that: Any): Boolean = true
      |  def isEmpty = false
      |  def get = new Result
      |  def _1 = "s"
      |  def _2 = 1
      |
      |object A:
      |  def unapply(i: Int): Unapplied = new Unapplied
      |
      |val A(r) = 1
      |val _r: Result = r
      |
      |{
      |  val A(s, i) = 1
      |  val _s: String = s
      |  val _i: Int = i
      |}
      |
      |{
      |  val A(b, i, s) = 1
      |  val _b: Boolean = b
      |  val _i: Int = i
      |  val _s: String = s
      |}
      |""".stripMargin
  ).map(testDataFromCode)
}
