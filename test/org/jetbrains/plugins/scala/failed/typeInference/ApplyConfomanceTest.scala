package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * @author anton.yalyshev
  * @since 14.04.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class ApplyConfomanceTest extends ScalaLightCodeInsightFixtureTestAdapter {


  def testSCL9119() = {
    val text =
      """object ApplyBug {
        |  class Foo {
        |    def apply(t: Int): Int = 2
        |  }
        |
        |  def foo = new Foo
        |  def a(i: Int): Int = new Foo()(i)
        |}
        |/* True */
      """.stripMargin
    checkTextHasNoErrors(text)
  }
}
