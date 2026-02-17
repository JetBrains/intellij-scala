package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class ImplicitParametersScala3Test extends ImplicitParametersTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_6

  def testSCL21117(): Unit = checkNoImplicitParameterProblems(
    s"""
       |object Main:
       |  trait T0 { def foo0: String = ??? }
       |  trait T1 extends T0 { def foo1: String = ??? }
       |  trait T2 extends T0 { def foo2: String = ??? }
       |
       |  implicit val b: T1 & T2 = new T1 with T2 {}
       |
       |object Other1:
       |  import Main.*
       |  summon[T1 | T2]
       |  summon[T1 & T2]
       |
       |object Other2:
       |  ${START}summon[Main.T1 | Main.T2]$END
       |""".stripMargin
  )

  def testSCL21117_2(): Unit = checkNoImplicitParameterProblems(
    s"""
       |object Main:
       |  trait T0 { def foo0: String = ??? }
       |  trait T1 extends T0 { def foo1: String = ??? }
       |  trait T2 extends T0 { def foo2: String = ??? }
       |
       |  implicit val b: T1 & T2 = new T1 with T2 {}
       |
       |object Other2:
       |  ${START}summon[Main.T1 & Main.T2]$END
       |""".stripMargin
  )

  def testSCL21488(): Unit = checkNoImplicitParameterProblems(
    s"""
       |object A {
       |  class Constraint[A, C]
       |  class UnionConstraint[A, C] extends Constraint[A, C]
       |  class IsUnion[A]
       |  class True
       |
       |  given[A]                        : Constraint[A, True] with {}
       |  given[A, C](using u: IsUnion[C]): UnionConstraint[A, C] = ???
       |  given[A]                        : IsUnion[A] = ???
       |
       |  ${START}summon[Constraint[String, True]]$END
       |}
       |""".stripMargin
  )

  def testSCL20670(): Unit = checkTextHasNoErrors(
    s"""
       |trait CaseClassName[A]:
       |  def get: String
       |
       |object CaseClassName:
       |  def derived[A](using a: scala.deriving.Mirror.Of[A]): CaseClassName[A] = new CaseClassName[A]:
       |    def get: String = a.toString
       |case class CoolClass(i: Int) derives CaseClassName
       |""".stripMargin
  )

  def testSCL23914(): Unit = checkTextHasNoErrors(
    s"""
       |object A {
       |  case class A(x: Int, y: String)
       |  implicitly[deriving.Mirror.ProductOf[A]].fromProduct(1 -> "a")
       |}
       |""".stripMargin
  )

  def testMirrorOfSealed(): Unit = checkNoImplicitParameterProblems(
    s"""
       |sealed trait Foo
       |case class Bar extends Foo
       |case object Baz extends Foo
       |sealed class Qux extends Foo
       |enum F extends Qux
       |
       |object A {
       |  ${START}implicitly[scala.deriving.Mirror.Of[Foo]]$END
       |}
       |""".stripMargin
  )

  def testMirrorNeg(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |trait Foo
         |case class Bar extends Foo
         |object Test { imp${CARET}licitly[scala.deriving.Mirror.Of[Foo]] }
         |""".stripMargin
    )

    checkHasErrorAroundCaret(
      s"""
         |sealed trait Foo
         |class Bar extends Foo
         |object Test { imp${CARET}licitly[scala.deriving.Mirror.Of[Foo]] }
         |""".stripMargin
    )
  }

  def testPolyFunctionContextBound(): Unit = checkNoImplicitParameterProblems(
    s"""
      |trait Ord[A]
      |def compare[A: Ord](x: A, y: A): Int = ???
      |val comparer = [X: Ord as o] => (x: X, y: X) => ${START}compare(x, y)$END
      |""".stripMargin
  )

  def testNewStyleGivenParameters1(): Unit = checkNoImplicitParameterProblems(
    s"""
       |trait Ord[A] { def compare(x: A, y: A): Int }
       |given [A] => Ord[A] => Ord[List[A]] {
       |  override def compare(xs: List[A], ys: List[A]): Int = {
       |    ${START}summon[Ord[A]]$END
       |    1
       |  }
       |}
       |""".stripMargin
  )

  def testNewStyleGivenParameters2(): Unit = checkNoImplicitParameterProblems(
    s"""
       |trait Ord[A] { def compare(x: A, y: A): Int }
       |given [A] => (ord: Ord[A]) => Ord[List[A]] {
       |  override def compare(xs: List[A], ys: List[A]): Int = {
       |    ${START}summon[Ord[A]]$END
       |    1
       |  }
       |}
       |""".stripMargin
  )

  def testNewStyleGivenParameters3(): Unit = checkNoImplicitParameterProblems(
    s"""
       |trait Ord[A] { def compare(x: A, y: A): Int }
       |given [A: Ord as ord] => Ord[List[A]] {
       |  override def compare(xs: List[A], ys: List[A]): Int = {
       |    ${START}summon[Ord[A]]$END
       |    1
       |  }
       |}
       |""".stripMargin
  )


  def testSCL24031(): Unit =
    checkHasImplicitArgumentProblems(
      s"""
         |object A {
         |  trait A; trait B
         |  given A => B = ???
         |  ${START}implicitly[B]$END
         |}
         |""".stripMargin
      )

  def testSCL21517(): Unit =
    checkTextHasNoErrors(
      """
        |object Example:
        |  // OK: Inheritors of java.lang.Number
        |  // Resolves to scala.CanEqual.canEqualNumber
        |  summon[CanEqual[java.lang.Number, java.lang.Number]]
        |  summon[CanEqual[scala.math.ScalaNumber, scala.math.ScalaNumber]]
        |  summon[CanEqual[java.lang.Float, java.lang.Float]]
        |  summon[CanEqual[scala.math.ScalaNumber, scala.math.ScalaNumber]]
        |  summon[CanEqual[java.math.BigDecimal, java.math.BigDecimal]]
        |  summon[CanEqual[java.lang.Short, java.lang.Short]]
        |  summon[CanEqual[java.lang.Long, java.lang.Long]]
        |  summon[CanEqual[java.util.concurrent.atomic.AtomicInteger, java.util.concurrent.atomic.AtomicInteger]]
        |  summon[CanEqual[java.lang.Byte, java.lang.Byte]]
        |  summon[CanEqual[scala.math.ScalaNumber, scala.math.ScalaNumber]]
        |  summon[CanEqual[java.lang.Double, java.lang.Double]]
        |  summon[CanEqual[java.util.concurrent.atomic.AtomicLong, java.util.concurrent.atomic.AtomicLong]]
        |  summon[CanEqual[java.lang.Integer, java.lang.Integer]]
        |  summon[CanEqual[java.math.BigInteger, java.math.BigInteger]]
        |
        |  // OK: String
        |  // Resolves to scala.CanEqual.canEqualString
        |  summon[CanEqual[java.lang.String, java.lang.String]]
        |  summon[CanEqual[String, String]]
        |
        |  // NOT OK: Scala primitive types (scala.AnyVal)
        |  summon[CanEqual[Int, Int]]
        |  summon[CanEqual[Float, Float]]
        |  summon[CanEqual[Double, Double]]
        |  summon[CanEqual[Long, Long]]
        |  summon[CanEqual[Boolean, Boolean]]
        |  summon[CanEqual[Char, Char]]
        |  summon[CanEqual[Unit, Unit]]
        |
        |  summon[CanEqual[AnyVal, AnyVal]]
        |  summon[CanEqual[AnyRef, AnyRef]]
        |  summon[CanEqual[Object, Object]]
        |  summon[CanEqual[Object, AnyRef]]
        |
        |  summon[CanEqual[MyBaseClass, Null]]
        |  summon[CanEqual[Null, MyBaseClass]]
        |  summon[CanEqual[Nothing, MyBaseClass]]
        |  summon[CanEqual[MyBaseClass, Nothing]]
        |
        |  // NOT OK: Other Java/Scala classes including custom classes
        |  summon[CanEqual[scala.util.Random, scala.util.Random]]
        |  summon[CanEqual[MyBaseClass, MyBaseClass]]
        |  summon[CanEqual[MyChildClass, MyChildClass]]
        |
        |abstract class MyBaseClass
        |class MyChildClass extends MyBaseClass
        |""".stripMargin
    )

  def testSCL21517Neg_StrictEq(): Unit = checkHasImplicitArgumentProblems(
    s"""
       |object A {
       |  import scala.language.strictEquality
       |  class Foo
       |  ${START}summon[CanEqual[Foo, Foo]]$END
       |}
       |""".stripMargin
  )

  def testSCL21517Neg_HasInstance(): Unit = checkHasImplicitArgumentProblems(
    s"""
       |object A {
       |  class Foo
       |  class Bar extends Foo
       |  given CanEqual[Bar, Bar] = ???
       |  ${START}summon[CanEqual[Foo, Bar]]$END
       |}
       |""".stripMargin
  )
}
