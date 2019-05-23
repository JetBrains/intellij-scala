package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.project._
import org.junit.experimental.categories.Category

@Category(Array(classOf[PerfCycleTests]))
class PartialUnificationHighlightingTest extends ScalaLightCodeInsightFixtureTestAdapter {
  override def setUp(): Unit = {
    super.setUp()
    getModule.scalaCompilerSettings.additionalCompilerOptions = Seq("-Ypartial-unification")
  }

  def testSCL11306(): Unit = {
    val code =
      """
        |object T {
        |  class A[F[_]]
        |  class B[F[_]]
        |  class C[F[_]]
        |
        |  final case class Prod[F[_[_]], G[_[_]], A[_]](fa: F[A], ga: G[A])
        |
        |  val x = Prod(new A[List], Prod(new B[List], new C[List]))
        |}
      """.stripMargin
    checkTextHasNoErrors(code)
  }

  def testSCL11227(): Unit = {
    val code =
      """
        |object Demo1b {
        |  class Foo[T, F[_]]
        |
        |  def meh[M[_[_]], F[_]](x: M[F]): M[F] = x
        |
        |  meh(new Foo[Int, List])
        |}
        |
        |object Demo1c {
        |  trait TC[T]
        |  class Foo[F[_], G[_]]
        |
        |  def meh[M[_[_]]](x: M[TC]): M[TC] = x
        |
        |  meh(new Foo[TC, TC])
        |}
        |
        |object Demo1d {
        |  trait TC[F[_]]
        |  trait TC2[F[_]]
        |  class Foo[F[_[_]], G[_[_]]]
        |  new Foo[TC, TC2]
        |
        |  def meh[M[_[_[_]]]](x: M[TC2]): M[TC2] = x
        |
        |  meh(new Foo[TC, TC2])
        |}
      """.stripMargin
    checkTextHasNoErrors(code)
  }

  def testSCL13532(): Unit = {
    val scalaText =
      """
        |object Test {
        |    def foo[F[_], A](a: F[F[A]]): Any = ???
        |    def either1: Either[String, Either[String, Int]] = ???
        |    foo(either1)
        |}
      """.stripMargin
    checkTextHasNoErrors(scalaText)
  }
}
