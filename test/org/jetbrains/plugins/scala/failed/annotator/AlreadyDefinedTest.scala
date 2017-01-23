package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * User: Dmitry.Naydanov
  * Date: 27.03.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class AlreadyDefinedTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL2101(): Unit =
    checkTextHasNoErrors(
      """
        |class Some(name: Int) {
        |    def name {""}
        |}
      """.stripMargin)

  def testSCL5789(): Unit =
    checkTextHasNoErrors(
      """
        |class Test {
        |  private[this] val x = 1
        |  def x() = 2
        |}
      """.stripMargin)

  def testSCL11277(): Unit =
    checkTextHasNoErrors(
      """
        |trait Functor[F[_]] {
        |  def map[A, B](fa: F[A])(f: A => B): F[B]
        |}
        |
        |trait Applicative[F[_]] extends Functor[F] {
        |  def unit[A](a: A): F[A]
        |
        |  def apply[A, B](fa: F[A])(fab: F[A => B]): F[B] =
        |    map2(fa, fab)((a, ab) => ab(a))
        |
        |  def map2[A, B, C](fa: F[A], fb: F[B])(f: (A, B) => C): F[C] =
        |    apply(fb)(map(fa)(f.curried))
        |
        |  def map[A, B](fa: F[A])(f: A => B): F[B] =
        |    map2(fa, unit(()))((a, _) => f(a))
        |
        |  def compose[G[_]](G: Applicative[G]): Applicative[({type f[x] = F[G[x]]})#f] = {
        |    val self = this
        |    new Applicative[({type f[x] = F[G[x]]})#f] {
        |      def unit[A](a: A): F[G[A]] = self.unit(G.unit(a))
        |    }
        |  }
        |}
      """.stripMargin)
}
