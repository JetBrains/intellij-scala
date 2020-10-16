package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FeatureSpecNewGenerator

// from 3.1.0
trait FeatureSpecSingleTestNewTest extends FeatureSpecNewGenerator {

  val featureSpecConfigTestName = "Feature: Feature 1 Scenario: Scenario A"
  val featureSpecTaggedConfigTestName = "Feature: Feature 3 Scenario: Tagged"
  val featureSpecTestPath = TestNodePath("[root]", featureSpecNewClassName, "Feature: Feature 1", "Scenario: Scenario A")
  val featureSpecTaggedTestPath = TestNodePath("[root]", featureSpecNewClassName, "Feature: Feature 3", "Scenario: Tagged")

  def testFeatureSpecSingleTest(): Unit = {
    runTestByLocation(loc(featureSpecNewFileName, 5, 7),
      assertConfigAndSettings(_, featureSpecNewClassName, featureSpecConfigTestName),
      root => {
        assertResultTreeHasExactNamedPaths(root)(Seq(featureSpecTestPath))
        assertResultTreeDoesNotHaveNodes(root, "Scenario: Scenario B")
      }
    )
  }

  def testTaggedFeatureSpecTest(): Unit = {
    runTestByLocation(loc(featureSpecNewFileName, 24, 7),
      assertConfigAndSettings(_, featureSpecNewClassName, featureSpecTaggedConfigTestName),
      root => {
        assertResultTreeHasExactNamedPaths(root)(Seq(featureSpecTaggedTestPath))
        assertResultTreeDoesNotHaveNodes(root, "Scenario: Scenario A")
      }
     )
  }
}
