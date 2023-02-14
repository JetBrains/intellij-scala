package org.jetbrains.plugins.scala.testingSupport.scalatest.base.singleTest.tagged

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators.FreeSpecGenerator

trait FreeSpecTaggedSingleTestTest extends FreeSpecGenerator {

  protected val freeSpecTaggedTestPath = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", freeSpecClassName, "A FreeSpecTest", "can be tagged")

  def testTaggedFreeSpec(): Unit = {
    runTestByLocation(loc(freeSpecFileName, 12, 7),
      assertConfigAndSettings(_, freeSpecClassName, "A FreeSpecTest can be tagged"),
      root => {
        assertResultTreeHasSinglePath(root, freeSpecTaggedTestPath)
      }
    )
  }
}
