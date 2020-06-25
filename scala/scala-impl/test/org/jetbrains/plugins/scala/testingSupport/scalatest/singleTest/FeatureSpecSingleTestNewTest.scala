package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FeatureSpecNewGenerator

// from 3.1.0
trait FeatureSpecSingleTestNewTest extends FeatureSpecNewGenerator {

  val featureSpecConfigTestName = "Feature: Feature 1 Scenario: Scenario A"
  val featureSpecTaggedConfigTestName = "Feature: Feature 3 Scenario: Tagged"
  val featureSpecTestPath = List("[root]", featureSpecNewClassName, "Feature: Feature 1", "Scenario: Scenario A")
  val featureSpecTaggedTestPath = List("[root]", featureSpecNewClassName, "Feature: Feature 3", "Scenario: Tagged")

  def testFeatureSpecSingleTest(): Unit = {
    runTestByLocation2(5, 7, featureSpecNewFileName,
      assertConfigAndSettings(_, featureSpecNewClassName, featureSpecConfigTestName),
      root => {
        assertResultTreeHasExactNamedPaths(root)(Seq(featureSpecTestPath))
        assertResultTreeDoesNotHaveNodes(root, "Scenario: Scenario B")
      }
    )
  }

  def testTaggedFeatureSpecTest(): Unit = {
    runTestByLocation2(24, 7, featureSpecNewFileName,
      assertConfigAndSettings(_, featureSpecNewClassName, featureSpecTaggedConfigTestName),
      root => {
        assertResultTreeHasExactNamedPaths(root)(Seq(featureSpecTaggedTestPath))
        assertResultTreeDoesNotHaveNodes(root, "Scenario: Scenario A")
      }
     )
  }
}
