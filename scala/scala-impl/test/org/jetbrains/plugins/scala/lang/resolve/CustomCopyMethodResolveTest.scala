package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class CustomCopyMethodResolveTest extends ScalaLightCodeInsightFixtureTestAdapter with SimpleResolveTestBase {
  import SimpleResolveTestBase._

  def testSCL15809(): Unit = doResolveTest(
    s"""
       |case class Example(foo: Int) {
       |  private def co${REFTGT}py(foo: Int = this.foo): Example = {
       |    Example(foo + 1)
       |  }
       |  def increase(value: Int): Example = {
       |    cop${REFSRC}y(foo = value)
       |  }
       |}
       |""".stripMargin
  )
}
