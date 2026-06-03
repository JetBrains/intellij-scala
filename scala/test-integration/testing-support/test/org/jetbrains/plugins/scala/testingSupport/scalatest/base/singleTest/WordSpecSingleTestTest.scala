package org.jetbrains.plugins.scala.testingSupport.scalatest.base.singleTest

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators.WordSpecGenerator

trait WordSpecSingleTestTest extends WordSpecGenerator {

  protected val wordSpecTestPath = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", wordSpecClassName, "WordSpecTest", "Run single test")
  protected val wordSpecTestTaggedPath = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", wordSpecClassName, "tagged", "be tagged")

  def testWordSpec(): Unit =
    runTestByLocation(loc(wordSpecFileName, 5, 10),
      assertConfigAndSettings(_, wordSpecClassName, "WordSpecTest should Run single test"),
      root => {
        assertResultTreeHasSinglePath(root, wordSpecTestPath)
      }
    )

  def testTaggedWordSpec(): Unit =
    runTestByLocation(loc(wordSpecFileName, 20, 6),
      assertConfigAndSettings(_, wordSpecClassName, "tagged should be tagged"),
      root => {
        assertResultTreeHasSinglePath(root, wordSpecTestTaggedPath)
      }
    )
}
