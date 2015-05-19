package org.jetbrains.plugins.scala.testingSupport.scalatest.scopeTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FeatureSpecGenerator

/**
 * @author Roman.Shein
 * @since 05.05.2015.
 */
trait FeatureSpecScopeTest extends FeatureSpecGenerator {
  def testFeatureSpecEmptyScope() {
    addFeatureSpec()

    assert(checkConfigAndSettings(createTestFromLocation(21, 7, featureSpecFileName), featureSpecClassName))
  }

  def testFeatureSpecScope() {
    addFeatureSpec()
    val testName = "Feature: Feature 1 Scenario: Scenario A\nFeature: Feature 1 Scenario: Scenario B"
    val aPath = List("[root]", "FeatureSpecTest", "Feature: Feature 1", "Scenario: Scenario A")
    val bPath = List("[root]", "FeatureSpecTest", "Feature: Feature 1", "Scenario: Scenario B")

    runTestByLocation(3, 10, featureSpecFileName, checkConfigAndSettings(_, featureSpecClassName, testName),
      root => checkResultTreeHasExactNamedPath(root, aPath:_*) &&
          checkResultTreeHasExactNamedPath(root, bPath:_*) && checkResultTreeDoesNotHaveNodes(root, "Feature: Feature 2"))
  }

}
