package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class HKExpectedTypeConformanceTest extends ScalaLightCodeInsightFixtureTestCase {
  def testSCL13042(): Unit = {
    val text =
      """
        |def f[R[_], T](fun: String => R[T]): String => R[T] = fun
        |val result = f(str => Option(str))
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testSCL14179(): Unit = {
    val text =
      """
        |trait Functor[F[_]] {
        |  def map[A, B](fa: F[A])(f: A => B): F[B]
        |}
        |trait Applicative[F[_]] extends Functor[F] { self =>
        |  def map2[A, B, C](fa: F[A], fb: F[B])(f: (A, B) => C): F[C] =
        |    apply(map(fa)(f.curried))(fb)
        |  def apply[A, B](fab: F[A => B])(fa: F[A]): F[B] =
        |    map2(fab, fa)(_(_))
        |  def map[A, B](fa: F[A])(f: A => B): F[B] =
        |    apply(unit(f))(fa)
        |  def unit[A](a: => A): F[A]
        |}
        |trait Traverse[F[_]] extends Functor[F] {
        |  def traverse[G[_]: Applicative, A, B](fa: F[A])(f: A => G[B]): G[F[B]] =
        |    sequence(map(fa)(f))
        |  def sequence[G[_]: Applicative, A](fma: F[G[A]]): G[F[A]] =
        |    traverse(fma)(ma => ma)
        |}
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testSCL23561(): Unit = checkTextHasNoErrors(
    """
      |object Test {
      |  trait BuildFrom[-From, -A, +C]
      |  def useBuildFrom[B, That](b: B)(bf: BuildFrom[Seq[Any], B, That]): That = ???
      |  def buildFromIterableOps[CC[X] <: Iterable[X], A0, A]: BuildFrom[CC[A0], A, CC[A]] = ???
      |  def acceptSeq[A](as: Seq[A]): Seq[A] = ???
      |
      |  acceptSeq {
      |    useBuildFrom("")(buildFromIterableOps)
      |  }
      |    .head
      |    .chars() // Good code red
      |}
      |""".stripMargin
  )
}
