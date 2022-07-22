package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
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
