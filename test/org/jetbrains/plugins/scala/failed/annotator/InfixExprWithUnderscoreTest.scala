package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  * @since 01.04.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class InfixExprWithUnderscoreTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL5728(): Unit =
    checkTextHasNoErrors(
      """
        |class SCL5728 {
        |
        |  object B {
        |    def f(x: Int, y: Int) {
        |    }
        |  }
        |
        |  val f1 = B f(_, _) //compiles, does not highlighted
        |//f1: (Int, Int) => Unit = <function2>
        |
        |  val f2: (Int, Int) => Unit = B f(_, _) //compiles, but highlighted
        |//f2: (Int, Int) => Unit = <function2>
        |}
      """.stripMargin)
}
