package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * Created by Anton Yalyshev on 29/08/16.
  */
@Category(Array(classOf[PerfCycleTests]))
class ImportsResolveTest extends FailedResolveCaretTestBase {

  def testSCL10725a: Unit = {
    doResolveCaretTest(
      """
        |object Example01 {
        |  import Helper._
        |
        |  test.<caret>foo
        |}
        |
        |object Helper {
        |  object test {
        |    def foo = 1
        |  }
        |}
      """.stripMargin
    )
  }

  def testSCL10725b: Unit = {
    doResolveCaretTest(
      """
        |object Example02 {
        |
        |  class Inner1(val foo: Int)
        |
        |  class Inner2(foo: String) extends Inner1(0) {
        |
        |    import <caret>foo._
        |    charAt(0)
        |  }
        |
        |}
      """.stripMargin
    )
  }
}
