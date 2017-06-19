package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * @author Alefas
  * @since 29/08/16
  */
class ParameterizedTypeTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL4990() = {
    val text =
      """
        |object Example {
        |  def mangle[A, M[_]](m: M[A]) = {}
        |
        |  case class OneParameterType[A](value: A)
        |  case class TwoParameterType[S, A](value: (S, A))
        |  type Alias[A] = TwoParameterType[String, A]
        |
        |  val a: OneParameterType[Int] = OneParameterType(1)
        |  val b: Alias[Int] = TwoParameterType(("s", 1))
        |  mangle(a)
        |  mangle(b)
        |}
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testSCL8161() = {
    val text =
      """
        |trait TypeMismatch {
        |  type M[X]
        |
        |  def ok[F[_],A](v: F[A]) = ???
        |
        |  ok(??? : M[Option[Int]])
        |}
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testSCL10168() = {
    checkTextHasNoErrors(
      """  trait Members {
        |  type F[_]
        |  type A
        |  val value: F[A]
        |}
        |object Scratch {
        |  def meth[F[_], A](fa: F[A]) = {}
        |
        |  def callMethWithValue[F[_], A](members: Members) =
        |    meth(members.value)
        |}""".stripMargin
    )
  }

  def testSCL6384() = {
    val text =
      """
        |object Test {
        |  class R[A](f: List[A] => A)(g: A => Any)
        |  def f: List[String] => String = _.foldRight("")(_+_)
        |  val r = new R(f)(_.substring(3))
        |}
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testSCL9555() = {
    val text =
      """
        |object Test {
        |  case class PrintedColumn[T](
        |                               name: String,
        |                               value: T => Any,
        |                               color: T => String = { _: T => "blue" })
        |  case class Foo(a: Int, b: String)
        |  val col: PrintedColumn[Foo] = PrintedColumn("a", _.a)
        |}
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testSCL10149() = {
    val text =
      """
        |object SCL10149{
        |
        |  trait Functor[F[_]] {
        |    def map[A, B](fa: F[A])(f: A => B): F[B]
        |  }
        |
        |  trait Applicative[F[_]] extends Functor[F] {
        |    def apply[A, B](fab: F[A => B])(fa: F[A]): F[B] =
        |      map2(fab, fa)((ab, a) => ab(a))
        |
        |    def unit[A](a: => A): F[A]
        |
        |    def map2[A, B, C](fa: F[A], fb: F[B])(f: (A, B) => C): F[C] =
        |      apply(apply(unit(f.curried))(fa))(fb)
        |  }
        |}
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testSCL10151() = {
    val text =
      """
        |object Test {
        |
        |  case class Failure[E](head: E, tail: Vector[E] = Vector.empty[E])
        |
        |  val v1: Failure[String] = Failure("zonk")
        |}
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testSCL10264() = {
    checkTextHasNoErrors(
      """
        |import scala.language.higherKinds
        |
        |trait Functor [F[_]] {
        |  def map [A, B] (fa: F[A]) (f: A => B): F[B]
        |}
        |
        |trait Applicative [F[_]] extends Functor[F] {
        |  def apply [A, B] (fab: F[A => B]) (fa: F[A]): F[B] = map2(fab, fa) (_(_))
        |  def unit [A] (a: => A): F[A]
        |  def map [A, B] (fa: F[A]) (f: A => B): F[B] = apply(unit(f))(fa) // <-- (fa) is highlighted, error message: "Type mismatch, expected: F[Nothing], actual: F[A]"
        |  def map2 [A, B, C] (fa: F[A], fb: F[B]) (f: (A, B) => C): F[C]
        |}
        |""".stripMargin
    )
  }
}
