package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * @author Alefas
  * @since 29/08/16
  */
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

  def testSCL10168() = {
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
}
