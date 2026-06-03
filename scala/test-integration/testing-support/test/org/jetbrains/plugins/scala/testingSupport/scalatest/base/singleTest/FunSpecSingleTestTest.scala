package org.jetbrains.plugins.scala.testingSupport.scalatest.base.singleTest

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators.FunSpecGenerator

trait FunSpecSingleTestTest extends FunSpecGenerator {

  protected val funSpecTestPath = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", funSpecClassName, "FunSpecTest", "should launch single test")
  protected val funSpecTaggedTestPath = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", funSpecClassName, "taggedScope", "is tagged")

  def testFunSpec(): Unit = {
    runTestByLocation(loc(funSpecFileName, 5, 9),
      assertConfigAndSettings(_, funSpecClassName, "FunSpecTest should launch single test"),
      root => {
        assertResultTreeHasSinglePath(root, funSpecTestPath)
      }
    )
  }

  def testTaggedFunSpec(): Unit = {
    runTestByLocation(loc(funSpecFileName, 20, 6),
      assertConfigAndSettings(_, funSpecClassName, "taggedScope is tagged"),
      root => {
        assertResultTreeHasSinglePath(root, funSpecTaggedTestPath)
      }
    )
  }
}
