package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * Created by Anton.Yalyshev on 29/04/16.
  */
@Category(Array(classOf[PerfCycleTests]))
class StringResolveTest extends FailedResolveCaretTestBase {

  def testSCL8414a(): Unit = {
    doResolveCaretTest(
      """
        |import org.specs2._
        |class QuickStartSpec extends Specification { def is = <caret>s2"test"
        |}
      """.stripMargin)
  }

}
