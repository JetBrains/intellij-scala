package org.jetbrains.plugins.scala.testingSupport.scalatest.base.singleTest

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators.FreeSpecGenerator

trait FreeSpecSingleTestTest extends FreeSpecGenerator {

  protected val freeSpecTestPath = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", freeSpecClassName, "A FreeSpecTest", "should be able to run single tests")
  protected val freeSpecNonNestedTestPath = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", complexFreeSpecClassName, "Not nested scope")

  def testFreeSpec(): Unit =
    runTestByLocation(loc(freeSpecFileName, 6, 3),
      assertConfigAndSettings(_, freeSpecClassName, "A FreeSpecTest should be able to run single tests"),
      root => {
        assertResultTreeHasSinglePath(root, freeSpecTestPath)
      }
    )

  def testFreeSpecNonNested(): Unit =
    runTestByLocation(loc(complexFreeSpecFileName, 33, 15),
      assertConfigAndSettings(_, complexFreeSpecClassName, "Not nested scope"),
      root => {
        assertResultTreeHasSinglePath(root, freeSpecNonNestedTestPath)
      }
    )
}
