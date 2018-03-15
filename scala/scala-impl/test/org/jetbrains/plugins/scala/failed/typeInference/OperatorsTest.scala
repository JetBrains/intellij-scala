package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * Created by kate on 3/24/16.
  */

@Category(Array(classOf[PerfCycleTests]))
class OperatorsTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  def testSCL8595() = {
    val text =
      """
        |def test(b: Boolean, i: Int): Boolean = b == /*start*/i < 4/*end*/
        |
        |//Boolean""".stripMargin
    doTest(text)
  }

  def testSCL5723(): Unit = doTest {
    """
      |trait Foo {
      |  def `\\`(bar: Int): Int = 1
      |
      |  def test(f: Foo) {
      |    /*start*/f \ 33/*end*/
      |  }
      |}
      |//Int
    """.stripMargin.trim
  }
}
