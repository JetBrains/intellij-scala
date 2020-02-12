package org.jetbrains.plugins.scala.lang.resolve
import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_12}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class ParenlessMethodCallOverloadingResolutionTest
    extends ScalaLightCodeInsightFixtureTestAdapter
    with SimpleResolveTestBase {
  import SimpleResolveTestBase._

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= Scala_2_12

  def testSCL16802(): Unit = doResolveTest(
    s"""
       |trait Foo {
       |  def foo(i: Int): String
       |}
       |
       |def ge${REFTGT}tFoo(): Foo = ???
       |def getFoo(s: String): Foo = ???
       |
       |def takesFoo(foo: Foo): Unit = ()
       |takesFoo(getF${REFSRC}oo)
       |
       |""".stripMargin)

}
