package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * @author Alefas
  * @since 29/08/16
  */
class ParameterizedTypeTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL4990(): Unit = {
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

  def testSCL8161(): Unit = {
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

  def testSCL10168(): Unit = {
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

  def testSCL6384(): Unit = {
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

  def testSCL9555(): Unit = {
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

  def testSCL10149(): Unit = {
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

  def testSCL10151(): Unit = {
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

  def testSCL10264(): Unit = {
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

  def testSCL7891(): Unit = {
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

  def testSCL10156(): Unit = {
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

  //this test is intermittent!
  def testSCL10399(): Unit = {
    val fileText =
      s"""
         |trait AA[T]
         |trait QQ[T] extends AA[T]
         |
         |class Z extends QQ[AnyRef]
         |
         |object Example {
         |  def asFA[F[_], A](fa: F[A]): F[A] = fa
         |
         |  val z: QQ[AnyRef] = asFA(new Z) // sometimes type of the rhs is AA[T] instead of QQ[T]
         |}
     """.stripMargin
    checkTextHasNoErrors(fileText)
  }

  def testSCL11597(): Unit = {
    checkTextHasNoErrors(
      """trait Node
        |
        |  class A(val a: Int, val b: Int) extends Node
        |
        |  class DefExtractorSimple[T <: Node, X](func: T => X) {
        |    def unapply(arg: T): Option[X] = Some(func(arg))
        |  }
        |
        |  def extractSimple[T <: Node, X](func: T => X): DefExtractorSimple[T, X] = new DefExtractorSimple[T, X](func)
        |
        |  val extractA = extractSimple((arg: A) => (arg.a, arg.b))
        |
        |  object ExtractAA {
        |    def unapply(arg: Node): Option[Int] = arg match {
        |      case extractA(a, _) => Some(a)
        |      case _ => None
        |    }
        |  }""".stripMargin
    )
  }

  def testSCL14538(): Unit = checkTextHasNoErrors(
    """
      |final case class Foo[F[_], A](run: F[(A, F[Unit])])
      |
      |trait Lift[F[_]] {
      |  def lift[A](a: A): F[A]
      |}
      |
      |def pure[F[_], A](a: A)(implicit F: Lift[F]): Foo[F, A] =
      |  Foo(F.lift((a, F.lift(()))))
    """.stripMargin
  )

  def testSCL12656(): Unit = {
    checkTextHasNoErrors(
      """import scala.concurrent.{ExecutionContext, Future}
        |import scala.util.Success
        |
        |object TestCase {
        |  def f: Future[Any] = null
        |
        |  implicit class MyFuture[T](val f: Future[T]) {
        |    def awaitAndDo[U <: T](func: U => String)(implicit ec: ExecutionContext): String = {
        |      f onComplete {
        |        case Success(value) => return func(value.asInstanceOf[U])
        |        case _ => Unit
        |      }
        |      "bar"
        |    }
        |  }
        |
        |  private def foo = {
        |    implicit val ec: ExecutionContext = null
        |    var baz: String = f awaitAndDo[Option[String]] {
        |      case Some(s) => s
        |      case None => "oups"
        |    }
        |  }
        |}""".stripMargin
    )
  }
}
