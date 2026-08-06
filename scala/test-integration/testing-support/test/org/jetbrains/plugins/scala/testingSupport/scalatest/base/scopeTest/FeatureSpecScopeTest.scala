package org.jetbrains.plugins.scala.testingSupport.scalatest.base.scopeTest

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators.FeatureSpecGenerator

trait FeatureSpecScopeTest extends FeatureSpecGenerator {

  def testFeatureSpecEmptyScope(): Unit =
    assertConfigAndSettings(createTestCaretLocation(21, 7, featureSpecFileName), featureSpecClassName)

  def testFeatureSpecScope(): Unit = {
    val testNames = Seq("Feature: Feature 1 Scenario: Scenario A", "Feature: Feature 1 Scenario: Scenario B")
    val aPath = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", featureSpecClassName, "Feature: Feature 1", "Scenario: Scenario A")
    val bPath = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", featureSpecClassName, "Feature: Feature 1", "Scenario: Scenario B")

    runTestByLocation(loc(featureSpecFileName, 3, 10),
      assertConfigAndSettings(_, featureSpecClassName, testNames:_*),
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(aPath, bPath))
      }
    )
  }
}
