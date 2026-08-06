package org.jetbrains.plugins.scala.testingSupport.scalatest.base.singleTest

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators.FreeSpecPathGenerator

trait FreeSpecPathSingleTestTest extends FreeSpecPathGenerator {

  protected val freeSpecPathTestPath = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", freeSpecPathClassName, "A FreeSpecTest", "should be able to run single test")

  def testFreeSpecPath(): Unit = {
    runTestByLocation(loc(freeSpecPathFileName, 5, 15),
      assertConfigAndSettings(_, freeSpecPathClassName, "A FreeSpecTest should be able to run single test"),
      root => {
        assertResultTreeHasSinglePath(root, freeSpecPathTestPath)
      }
    )
  }
}
