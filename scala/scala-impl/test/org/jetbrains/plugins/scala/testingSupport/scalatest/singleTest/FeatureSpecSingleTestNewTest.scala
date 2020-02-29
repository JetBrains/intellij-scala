package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FeatureSpecNewGenerator

// from 3.1.0
trait FeatureSpecSingleTestNewTest extends FeatureSpecNewGenerator {

  val featureSpecConfigTestName = "Feature: Feature 1 Scenario: Scenario A"
  val featureSpecTaggedConfigTestName = "Feature: Feature 3 Scenario: Tagged"
  val featureSpecTestPath = List("[root]", featureSpecNewClassName, "Feature: Feature 1", "Scenario: Scenario A")
  val featureSpecTaggedTestPath = List("[root]", featureSpecNewClassName, "Feature: Feature 3", "Scenario: Tagged")

  def testFeatureSpecSingleTest(): Unit = {
    runTestByLocation(5, 7, featureSpecNewFileName,
      checkConfigAndSettings(_, featureSpecNewClassName, featureSpecConfigTestName),
      root => checkResultTreeHasExactNamedPath(root, featureSpecTestPath:_*) &&
          checkResultTreeDoesNotHaveNodes(root, "Scenario: Scenario B")
    )
  }

  def testTaggedFeatureSpecTest(): Unit = {
    runTestByLocation(24, 7, featureSpecNewFileName,
      checkConfigAndSettings(_, featureSpecNewClassName, featureSpecTaggedConfigTestName),
      root => checkResultTreeHasExactNamedPath(root, featureSpecTaggedTestPath:_*) &&
        checkResultTreeDoesNotHaveNodes(root, "Scenario: Scenario A")
     )
  }
}
