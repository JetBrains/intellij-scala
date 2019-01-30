package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

@Category(Array(classOf[PerfCycleTests]))
class RandomHighlightingBugs extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL9738(): Unit = {
    checkTextHasNoErrors(
      s"""
         |sealed trait FeedbackReason
         |case object CostReason extends FeedbackReason
         |case object BugsReason extends FeedbackReason
         |case object OtherReason extends FeedbackReason
         |
         |object FeedbackTypes {
         |  def asMap(): Map[FeedbackReason, String] = {
         |    val reasons = Map(
         |      CostReason -> "It's too expensive",
         |      BugsReason -> "It's buggy"
         |    )
         |    reasons ++ Map(OtherReason -> "Some other reason")
         |  }
         |}
      """.stripMargin)
  }

  def testSCL14533(): Unit =
    checkTextHasNoErrors(
      """
        |trait Implicit[F[_]]
        |trait Context {
        |  type IO[T]
        |  implicit val impl: Implicit[IO] = new Implicit[IO] {}
        |}
        |class Component[C <: Context](val c: C { type IO[T] = C#IO[T] }) {
        |  import c._
        |  def x(): Unit = {
        |    val a: Implicit[c.IO] = c.impl
        |    val b: Implicit[C#IO] = c.impl
        |  }
        |}
        |
      """.stripMargin)

  def testSCL14700(): Unit =
    checkTextHasNoErrors(
      """
        |type Id[A] = A
        |val opt1: Id[Option[String]] = Some("Foo")
        |opt1.withFilter(one => true).map(one => takesString(one))
        |opt1.filter(one => true).map(one => takesString(one))
        |def takesString(foo: String): Unit = println(foo)
      """.stripMargin
    )

  def testSCL14486(): Unit =
    checkTextHasNoErrors(
      """
        |trait CovariantBifunctorMonad[F[+_, +_]] {
        |  def pure[A](a: A): F[Nothing ,A]
        |  def fail[E](e: E): F[E, Nothing]
        |  def flatMap[E, E1 >: E, A, B](fa: F[E, A], fb: A => F[E1, B]): F[E1, B]
        |}
        |object CovariantBifunctorMonad {
        |  def apply[F[+_, +_]: CovariantBifunctorMonad]: CovariantBifunctorMonad[F] = implicitly
        |  implicit final class Syntax[F[+_, +_]: CovariantBifunctorMonad, E, A](fa: F[E, A]) {
        |    def flatMap[E1 >: E, B](fb: A => F[E1, B]): F[E1, B] = apply[F].flatMap(fa, fb)
        |    def map[B](f: A => B): F[E, B] = flatMap(f(_).pure)
        |  }
        |  implicit final class AnySyntax[A](a: A) {
        |    def pure[F[+_, +_]: CovariantBifunctorMonad]: F[Nothing, A] = apply[F].pure(a)
        |    def fail[F[+_, +_]: CovariantBifunctorMonad]: F[A, Nothing] = apply[F].fail(a)
        |  }
        |}
        |object App {
        |  import CovariantBifunctorMonad._
        |  def error[F[+_, +_] : CovariantBifunctorMonad]: F[Throwable, Unit] = new Throwable{}.fail
        |  def generic[F[+_, +_] : CovariantBifunctorMonad]: F[Throwable, Int] =
        |    for {
        |      i <- 5.pure[F]
        |      _ <- error[F]
        |    } yield i
        |}
      """.stripMargin
    )

  def testSCL14745(): Unit =
    checkTextHasNoErrors(
      """
        |trait Category[F[_, _]] {
        |  def compose[A, B, C](f: F[B, C], g: F[A, B]): F[A, C]
        |}
        |
        |object test {
        |  implicit class OpticOps[T[_, _], A, B](val tab: T[A, B]) extends AnyVal{
        |    def >>>[Q[x, y] >: T[x, y], C](qbc: Q[B, C])(implicit cat: Category[Q]): Q[A, C] =
        |      cat.compose(qbc, tab)
        |  }
        |}
      """.stripMargin
    )

  def testSCL14586(): Unit =
    checkTextHasNoErrors(
      """
        |import scala.language.higherKinds
        |import scala.concurrent.Future
        |
        |case class EitherT[F[_], L, R](var value: F[Either[L, R]])
        |
        |sealed trait Parent
        |class Child extends Parent
        |class AnotherChild extends Parent
        |
        |object VarianceTests extends App {
        |  val future: Future[Either[Child, String]] = ???
        |  val eitherT2: EitherT[Future, Parent, String] = EitherT.apply(future)
        |}
      """.stripMargin
    )

  def testSCL4652(): Unit = checkTextHasNoErrors(
    s"""import scala.language.higherKinds
      |
      |  trait Binding[A]
      |
      |  trait ValueKey[BindingRoot] {
      |    def update(value: Any): Binding[BindingRoot]
      |  }
      |
      |  class Foo[A] {
      |    type ObjectType[B[_]] = B[A]
      |    val bar: ObjectType[Bar] = ???
      |  }
      |
      |  class Bar[A] {
      |    type ValueType = ValueKey[A]
      |    val qux: ValueType = ???
      |  }
      |
      |  object Test {
      |    def foo123(): Unit = {
      |      val g: Foo[String] => Binding[String] = _.bar.qux.update(1)
      |    }
      |  }
      |""".stripMargin
  )

  def testSCL14680(): Unit =
    checkTextHasNoErrors(
      """
        |object IntellijPartialUnification extends App {
        |  import scala.collection.generic.CanBuildFrom
        |  import scala.language.higherKinds
        |  trait X[M[_]]
        |  object X {
        |    implicit def toX[M[_], T](implicit cbf: CanBuildFrom[M[T], T, M[T]]): X[M] = new X[M] {}
        |  }
        |  implicitly[X[List]]
        |}
      """.stripMargin
    )

  def testSCL14468(): Unit =
    checkTextHasNoErrors(
      """
        |object Tag {
        |  type @@[+T, U] = T with Tagged[U]
        |  def tag[U] = new Tagger[U] {}
        |
        |  trait Tagged[U]
        |  trait Tagger[U] {
        |    def apply[T](t: T): T @@ U = ???
        |  }
        |}
        |
        |
        |import Tag._
        |trait TypedId[T] {
        |  type Id = String @@ T
        |}
        |
        |case class Test1(id: Test1.Id)
        |case class Test2(id: Test2.Id)
        |object Test1 extends TypedId[Test1]
        |object Test2 extends TypedId[Test2]
        |
        |
        |
        |object test {
        |  def newId[T](): String @@ T = tag[T][String]("something")
        |
        |  def testFn1(id: Test1.Id = newId()): Unit = { }
        |  def testFn2(id: Test1.Id = newId[Test1]()): Unit = { }
        |  testFn1()
        |  testFn2()
        |}
      """.stripMargin
    )

  def testSCL14897(): Unit = checkTextHasNoErrors(
    """
      |trait Bar
      |trait Foo { this: Bar =>
      |  abstract class TildeArrow[A, B]
      |  implicit object InjectIntoRequestTransformer extends Foo.this.TildeArrow[Int, Int]
      |}
      |
      |class Test extends Foo with Bar {
      |  val foo: Test.this.TildeArrow[Int, Int] = InjectIntoRequestTransformer // expected type error
      |}
    """.stripMargin
  )

  def testSCL14894(): Unit = checkTextHasNoErrors(
    """
      |import Container._
      |
      |object Container {
      |
      |  class Node[A] {
      |
      |    class WithFilter(p: A => Boolean) {
      |      def foreach[U](f: A => U): Unit = {}
      |    }
      |  }
      |}
      |
      |class Container[A] private (root: Node[A]) {
      |
      |  def withFilter(p: A => Boolean): Node[A]#WithFilter = new root.WithFilter(p)
      |}
    """.stripMargin
  )
}
