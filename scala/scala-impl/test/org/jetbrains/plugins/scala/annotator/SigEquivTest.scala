package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class SigEquivTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL11277(): Unit =
    checkTextHasNoErrors(
      """
        |trait Functor[F[_]] {
        |  def map[A, B](fa: F[A])(f: A => B): F[B]
        |}
        |
        |trait Applicative[F[_]] extends Functor[F] {
        |
        |  def map[A, B](fa: F[A])(f: A => B): F[B] = ???
        |
        |  def compose[G[_]]() = new Applicative[({type f[x] = F[G[x]]})#f] {}
        |}
      """.stripMargin)
}
