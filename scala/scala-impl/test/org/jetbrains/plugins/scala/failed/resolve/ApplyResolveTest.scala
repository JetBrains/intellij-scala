package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * Created by Anton Yalyshev on 15/04/16.
  */
@Category(Array(classOf[PerfCycleTests]))
class ApplyResolveTest extends FailedResolveCaretTestBase {

  def testSCL13705(): Unit = {
    doResolveCaretTest(
      """
        |case class Test(c: String)
        |
        |trait Factory {
        |  def apply(c: String): String = c
        |}
        |
        |object Test extends Factory {
        |  Test("").<caret>toLowerCase
        |}
      """.stripMargin)
  }
}
