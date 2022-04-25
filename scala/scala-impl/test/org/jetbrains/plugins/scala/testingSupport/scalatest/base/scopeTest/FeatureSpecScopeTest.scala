package org.jetbrains.plugins.scala.testingSupport.scalatest.base.scopeTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators.FeatureSpecGenerator

trait FeatureSpecScopeTest extends FeatureSpecGenerator {

  def testFeatureSpecEmptyScope(): Unit =
    assertConfigAndSettings(createTestCaretLocation(21, 7, featureSpecFileName), featureSpecClassName)

  def testFeatureSpecScope(): Unit = {
    val testNames = Seq("Feature: Feature 1 Scenario: Scenario A", "Feature: Feature 1 Scenario: Scenario B")
    val aPath = TestNodePath("[root]", featureSpecClassName, "Feature: Feature 1", "Scenario: Scenario A")
    val bPath = TestNodePath("[root]", featureSpecClassName, "Feature: Feature 1", "Scenario: Scenario B")

    runTestByLocation(loc(featureSpecFileName, 3, 10),
      assertConfigAndSettings(_, featureSpecClassName, testNames:_*),
      root => {
       assertResultTreeHasExactNamedPaths(root)(Seq(aPath, bPath))
        assertResultTreeDoesNotHaveNodes(root, "Feature: Feature 2")
      }
    )
  }
}
