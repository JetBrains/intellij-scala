package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FeatureSpecGenerator

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait FeatureSpecSingleTestTest extends FeatureSpecGenerator {
  //this is required because ScalaTest 1.9.2 has different convention for feature test names (without the 'Feature: ' prefix)
  val featureSpecConfigTestName = "Feature: Feature 1 Scenario: Scenario A"
  val featureSpecTestPath = List("[root]", "FeatureSpecTest", "Feature: Feature 1", "Scenario: Scenario A")

  def testFeatureSpec() {
    addFeatureSpec()

    runTestByLocation(5, 7, "FeatureSpecTest.scala",
      checkConfigAndSettings(_, "FeatureSpecTest", featureSpecConfigTestName),
      root => checkResultTreeHasExactNamedPath(root, featureSpecTestPath:_*) &&
          checkResultTreeDoesNotHaveNodes(root, "Scenario: Scenario B"),
      debug = true
    )
  }
}
