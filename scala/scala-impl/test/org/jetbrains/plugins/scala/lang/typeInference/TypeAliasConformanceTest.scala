package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class TypeAliasConformanceTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL17450(): Unit = checkTextHasNoErrors(
    """
      |object Opaque {
      |  type Opaque[+A]
      |  type OpaqueB = Opaque[Int]
      |  val b: OpaqueB = ???
      |  f(b)
      |  def f(id: Opaque[Any]): Any = ???
      |}
      |""".stripMargin
  )

  def testSCL16284(): Unit = checkTextHasNoErrors(
    """
      |trait Problem {
      |  type F[A]
      |  type G[A] <: F[A]
      |  val G: G[Int]
      |  val F: F[Int] = G
      |}""".stripMargin
  )
}
