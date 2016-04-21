package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * Created by Anton Yalyshev on 21/04/16.
  */
@Category(Array(classOf[PerfCycleTests]))
class PatternResolveTest extends FailedResolveCaretTestBase {

  def testSCL5895(): Unit = {
    doResolveCaretTest(
      """
        |  case class Bar[T](wrapped: T) {
        |    def method(some: T) = ???
        |  }
        |
        |  def bar(fooTuple: (Bar[T], T) forSome { type T }) = fooTuple match {
        |    case (a, b) => a.<caret>method(b)
        |  }
      """.stripMargin)

  }
}
