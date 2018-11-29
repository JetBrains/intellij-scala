package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class PartialUnificationTypeLambdaConformanceTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testTypeLambdaConformance(): Unit = checkTextHasNoErrors(
    """
      |object Foo {
      |  trait T[F[_]]
      |  def fun[F[_], A](fa: F[A])(tf: T[F]): T[F] = tf
      |  val f: Int => String = ???
      |  val t: T[({ type L[A] = Int => A})#L] = ???
      |  fun(f)(t)
      |}
    """.stripMargin
  )
}
