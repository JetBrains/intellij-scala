package org.jetbrains.plugins.scala.testingSupport.scalatest.base.scopeTest

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators.FreeSpecGenerator

trait FreeSpecScopeTest extends FreeSpecGenerator {

  def testFreeSpecEmptyScope(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(31, 7, complexFreeSpecFileName), complexFreeSpecClassName)
  }

  def testFreeSpecScope(): Unit = {
    val testNames = Seq(
      "A ComplexFreeSpec Outer scope 2 Inner scope 2 Another innermost scope",
      "A ComplexFreeSpec Outer scope 2 Inner test"
    )
    val path1 = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", complexFreeSpecClassName, "A ComplexFreeSpec", "Outer scope 2", "Inner scope 2", "Another innermost scope")
    val path2 = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", complexFreeSpecClassName, "A ComplexFreeSpec", "Outer scope 2", "Inner test")
    runTestByLocation(
      loc(complexFreeSpecFileName, 10, 10),
      assertConfigAndSettings(_, complexFreeSpecClassName, testNames:_*),
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          path1,
          path2
        ))
      })
  }
}
