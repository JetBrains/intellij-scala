package org.jetbrains.plugins.scala.testingSupport.scalatest.base.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators.FeatureSpecGenerator

trait FeatureSpecSingleTestTest extends FeatureSpecGenerator {

  //this is required because ScalaTest 1.9.2 has different convention for feature test names (without the 'Feature: ' prefix)
  protected val featureSpecConfigTestName = "Feature: Feature 1 Scenario: Scenario A"
  protected val featureSpecTaggedConfigTestName = "Feature: Feature 3 Scenario: Tagged"
  protected val featureSpecTestPath = TestNodePath("[root]", featureSpecClassName, "Feature: Feature 1", "Scenario: Scenario A")
  protected val featureSpecTaggedTestPath = TestNodePath("[root]", featureSpecClassName, "Feature: Feature 3", "Scenario: Tagged")

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
