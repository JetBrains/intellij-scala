package org.jetbrains.plugins.scala.lang.resolve
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class AnyMethodsOnTypeParamResolveTest extends ScalaLightCodeInsightFixtureTestAdapter with SimpleResolveTestBase {
  import SimpleResolveTestBase._

  def testSCL16905(): Unit = doResolveTest(
    s"""
       |def foo[F[_], A](fa: F[A]): Unit = {
       |  fa.toSt${REFSRC}ring
       |}
       |""".stripMargin
  )

  def testFirstOrderTypeParam(): Unit = doResolveTest(
    s"""
       |def foo[A](a: A): Unit = {
       |  a.hash${REFSRC}Code()
       |}
       |""".stripMargin
  )
}
