package org.jetbrains.plugins.scala.testingSupport.scalatest.scopeTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FeatureSpecGenerator

trait FeatureSpecScopeTest extends FeatureSpecGenerator {

  def testFeatureSpecEmptyScope(): Unit = {
    assertConfigAndSettings(createTestFromLocation(21, 7, featureSpecFileName), featureSpecClassName)
  }

  def testFeatureSpecScope(): Unit = {
    val testNames = Seq("Feature: Feature 1 Scenario: Scenario A", "Feature: Feature 1 Scenario: Scenario B")
    val aPath = List("[root]", featureSpecClassName, "Feature: Feature 1", "Scenario: Scenario A")
    val bPath = List("[root]", featureSpecClassName, "Feature: Feature 1", "Scenario: Scenario B")

    runTestByLocation(3, 10, featureSpecFileName, checkConfigAndSettings(_, featureSpecClassName, testNames:_*),
      root => checkResultTreeHasExactNamedPath(root, aPath:_*) &&
        checkResultTreeHasExactNamedPath(root, bPath:_*) &&
        checkResultTreeDoesNotHaveNodes(root, "Feature: Feature 2"))
  }
}
