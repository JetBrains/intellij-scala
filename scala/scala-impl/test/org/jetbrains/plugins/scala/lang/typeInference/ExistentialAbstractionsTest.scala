package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class ExistentialAbstractionsTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL17213(): Unit = checkHasErrorAroundCaret(
    s"""
       |
       |import scala.language.existentials
       |trait Eql[A, B]
       |
       |object A {
       |  val a: Eql[A, A] forSome { type A } = null
       |  val b: Eql[A, B] forSome { type A <: B; type B } = a
       |
       |  val b1: Eql[A, B] forSome { type A <: B; type B } = null
       |  val a1: Eql[A, A] forSome { type A } = ${CARET}b1
       |}
       |""".stripMargin
  )

  def testSCL17213Cov(): Unit = checkTextHasNoErrors(
    """
      |import scala.language.existentials
      |
      |trait Eql[+A, +B]
      |
      |object A {
      |  val a: Eql[A, A] forSome { type A } = null
      |  val b: Eql[A, B] forSome { type A <: B; type B } = a
      |
      |
      |  val b1: Eql[A, B] forSome { type A <: B; type B } = null
      |  val a1: Eql[A, A] forSome { type A } = b1
      |}
      |""".stripMargin
  )

  def testSCL17733(): Unit = checkTextHasNoErrors(
    """
      |trait ZIO[-R, +E, A]
      |trait Foo[F[_]]
      |
      |val b: Foo[({ type L[A] = ZIO[R, Throwable, A]] })#L forSome { type R } = null
      |val a: Foo[({ type L[A] = ZIO[R, E, A] })#L]         forSome { type R; type E } = b
      |""".stripMargin
  )
}
