package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * @author mucianm 
  * @since 07.04.16.
  */
class GenericsProjectionTest extends ScalaLightCodeInsightFixtureTestAdapter {

  def testSCL8969(): Unit = {
    checkTextHasNoErrors(
      """
        |trait Functor[F[_]] { self =>
        |  def map[A, B](fa: F[A])(f: A => B): F[B]
        |  def lift[A, B](f: A => B): F[A] => F[B] = fa => map(fa)(f)
        |  def as[A, B](fa: F[A], b: => B): F[B] = map(fa)(_ => b)
        |  def void[A](fa: F[A]): F[Unit] = as(fa, ())
        |
        |  def compose[G[_]](implicit G: Functor[G]): Functor[({type R[X] = F[G[X]]})#R] =
        |    new Functor[({type R[X] = F[G[X]]})#R] {
        |      def map[A, B](fga: F[G[A]])(f: A => B): F[G[B]] = self.map(fga)(ga => G.map(ga)(a => f(a)))
        |    }
        |}
      """.stripMargin)
  }
}
