package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  * @since 28.03.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
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

  def testSCL7891() = {
    val text =
      """
        |trait TestTrait[A, B] { def foo(a: A): B }
        |class TestClass[A, B] extends TestTrait[A, B] { override def foo(a: A): B = ??? }
        |class Test {
        |  type Trait[B] = TestTrait[Test, B]
        |  type Cls[B] = TestClass[Test, B]
        |  object tc extends Cls[Any] { //Shows error "Wrong number of type parameters. Expected: 2, actual: 1"
        |      override def foo(a: Test): Any = ???
        |    }
        |  }
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testSCL9014() = {
    val text =
      """
        |import scala.util.{Success, Try}
        |class Foo[A, In <: Try[A]] {
        |    def takeA(a: A) = a
        |    def takeIn(in: In) = {
        |      in match {
        |        case Success(a) ⇒ takeA(a) // cannot infer type
        |      }
        |    }
        |  }
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testSCL10118() = {
    val text =
      """
        |object SCL10118{
        |
        |  trait Test[A]
        |
        |  case class First(i: Int) extends Test[String]
        |
        |  case class Second(s: String) extends Test[Int]
        |
        |  def run[A](t: Test[A]): A = t match {
        |    case First(i) => i.toString
        |    case Second(s) => s.length
        |  }
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

  def testSCL10156() = {
    checkTextHasNoErrors(
      """  import scala.language.higherKinds
        |  import scala.language.reflectiveCalls
        |
        |  object Test {
        |
        |    trait Functor[F[_]] {
        |      def map[A, B](fa: F[A])(f: A => B): F[B]
        |    }
        |
        |    trait Applicative[F[_]] extends Functor[F] {
        |      self =>
        |      def apply[A, B](fab: F[A => B])(fa: F[A]): F[B] =
        |        map2(fab, fa)((ab, a) => ab(a))
        |
        |      def unit[A](a: => A): F[A]
        |
        |      def map[A, B](fa: F[A])(f: A => B): F[B] =
        |        apply[A, B](unit(f))(fa)
        |
        |      def map2[A, B, C](fa: F[A], fb: F[B])(f: (A, B) => C): F[C] =
        |        apply(apply[A, B => C](unit((a: A) => (b: B) => f(a, b)))(fa))(fb)
        |
        |      def compose[G[_]](G: Applicative[G]) =
        |        new Applicative[({type f[x] = F[G[x]]})#f] {
        |          override def unit[A](a: => A): F[G[A]] = self.unit(G.unit(a))
        |        }
        |    }
        |  }""".stripMargin
    )
  }
}
