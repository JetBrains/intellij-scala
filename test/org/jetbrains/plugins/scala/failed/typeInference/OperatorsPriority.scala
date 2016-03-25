package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * Created by kate on 3/24/16.
  */

@Category(Array(classOf[PerfCycleTests]))
class OperatorsPriority extends TypeInferenceTestBase {
  def testSCL8595() = {
    val text =
      """
        |def test(b: Boolean, i: Int): Boolean = b == /*start*/i < 4/*end*/
        |
        |//Boolean""".stripMargin
    doTest(text)
  }
}
