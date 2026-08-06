package org.jetbrains.plugins.scala.testingSupport.scalatest.base.singleTest

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators.PropSpecGenerator

trait PropSpecSingleTestTest extends PropSpecGenerator {

  protected val propSpecTestPath = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", propSpecClassName, "Single tests should run")
  protected val propSpecTestTaggedPath = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", propSpecClassName, "tagged")

  def testPropSpec(): Unit = {
    runTestByLocation(loc(propSpecFileName, 5, 5),
      assertConfigAndSettings(_, propSpecClassName, "Single tests should run"),
      root => {
        assertResultTreeHasSinglePath(root, propSpecTestPath)
      }
    )
  }

  def testTaggedPropSpec(): Unit = {
    runTestByLocation(loc(propSpecFileName, 12, 5),
      assertConfigAndSettings(_, propSpecClassName, "tagged"),
      root => {
        assertResultTreeHasSinglePath(root, propSpecTestTaggedPath)
      }
    )
  }
}
