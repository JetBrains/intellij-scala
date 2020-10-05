package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FeatureSpecGenerator

trait FeatureSpecSingleTestTest extends FeatureSpecGenerator {

  //this is required because ScalaTest 1.9.2 has different convention for feature test names (without the 'Feature: ' prefix)
  val featureSpecConfigTestName = "Feature: Feature 1 Scenario: Scenario A"
  val featureSpecTaggedConfigTestName = "Feature: Feature 3 Scenario: Tagged"
  val featureSpecTestPath = TestNodePath("[root]", featureSpecClassName, "Feature: Feature 1", "Scenario: Scenario A")
  val featureSpecTaggedTestPath = TestNodePath("[root]", featureSpecClassName, "Feature: Feature 3", "Scenario: Tagged")

  def testFeatureSpecSingleTest(): Unit =
    runTestByLocation(loc(featureSpecFileName, 5, 7),
      assertConfigAndSettings(_, featureSpecClassName, featureSpecConfigTestName),
      root => {
        assertResultTreeHasExactNamedPaths(root)(Seq(featureSpecTestPath))
        assertResultTreeDoesNotHaveNodes(root, "Scenario: Scenario B")
      }
    )

  def testTaggedFeatureSpecTest(): Unit =
    runTestByLocation(loc(featureSpecFileName, 24, 7),
      assertConfigAndSettings(_, featureSpecClassName, featureSpecTaggedConfigTestName),
      root => {
        assertResultTreeHasExactNamedPaths(root)(Seq(featureSpecTaggedTestPath))
        assertResultTreeDoesNotHaveNodes(root, "Scenario: Scenario A")
      }
    )
}
