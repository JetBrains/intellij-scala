package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

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
}
