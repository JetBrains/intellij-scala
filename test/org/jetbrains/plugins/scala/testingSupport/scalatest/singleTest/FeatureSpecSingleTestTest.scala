package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FeatureSpecGenerator

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait FeatureSpecSingleTestTest extends FeatureSpecGenerator {
  def testFeatureSpec() {
    addFeatureSpec()

    runTestByLocation(5, 7, "FeatureSpecTest.scala",
      checkConfigAndSettings(_, "FeatureSpecTest", "Feature: Feature 1 Scenario: Scenario A"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "FeatureSpecTest", "Feature: Feature 1", "Scenario: Scenario A") &&
          checkResultTreeDoesNotHaveNodes(root, "Scenario: Scenario B"),
      debug = true
    )
  }
}
