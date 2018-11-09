package org.jetbrains.plugins.scala.lang.resolve
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class PartialUnificationImplicitClassTest extends ScalaLightCodeInsightFixtureTestAdapter with SimpleResolveTestBase {
  import SimpleResolveTestBase._

  def testSCL14548(): Unit = doResolveTest(
    s"""
       |implicit class FooOps[F[_], A](self: F[A]) {
       |  def f${REFTGT}oo: Int = 0
       |}
       |
       |(null: Either[String, Int]).fo${REFSRC}o
     """.stripMargin
  )
}
