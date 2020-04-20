package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FeatureSpecGenerator

trait FeatureSpecSingleTestTest extends FeatureSpecGenerator {

  //this is required because ScalaTest 1.9.2 has different convention for feature test names (without the 'Feature: ' prefix)
  val featureSpecConfigTestName = "Feature: Feature 1 Scenario: Scenario A"
  val featureSpecTaggedConfigTestName = "Feature: Feature 3 Scenario: Tagged"
  val featureSpecTestPath = List("[root]", featureSpecClassName, "Feature: Feature 1", "Scenario: Scenario A")
  val featureSpecTaggedTestPath = List("[root]", featureSpecClassName, "Feature: Feature 3", "Scenario: Tagged")

  def testFeatureSpecSingleTest(): Unit = {
    runTestByLocation2(5, 7, featureSpecFileName,
      assertConfigAndSettings(_, featureSpecClassName, featureSpecConfigTestName),
      root => checkResultTreeHasExactNamedPath(root, featureSpecTestPath:_*) &&
          checkResultTreeDoesNotHaveNodes(root, "Scenario: Scenario B")
    )
  }

  def testTaggedFeatureSpecTest(): Unit = {
    runTestByLocation2(24, 7, featureSpecFileName,
      assertConfigAndSettings(_, featureSpecClassName, featureSpecTaggedConfigTestName),
      root => checkResultTreeHasExactNamedPath(root, featureSpecTaggedTestPath:_*) &&
        checkResultTreeDoesNotHaveNodes(root, "Scenario: Scenario A")
     )
  }
}
