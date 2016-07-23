package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * Created by Anton Yalyshev on 19/07/16.
  */
@Category(Array(classOf[PerfCycleTests]))
class ExistentialResolveTest extends FailedResolveCaretTestBase {

  def testSCL10548(): Unit = {
    doResolveCaretTest(
      """
        |type A = Set[X] forSome { type X <: <caret>Y; type Y <: Int}
      """.stripMargin)
  }

}
