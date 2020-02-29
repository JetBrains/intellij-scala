package org.jetbrains.plugins.scala.testingSupport.scalatest.finders

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators._

// from 3.1.0
trait FindersApiNewTest extends FindersApiBaseTest
  with FeatureSpecNewGenerator {

  def testFeatureSpecNew(): Unit = {
    val scenarioA = "Feature: Feature 1 Scenario: Scenario A"
    val scenarioB = "Feature: Feature 1 Scenario: Scenario B"

    //on 'scenario' word
    checkSelection(4, 8, featureSpecNewFileName, Set(scenarioA))
    checkSelection(8, 8, featureSpecNewFileName, Set(scenarioB))
    //on scenario name
    checkSelection(4, 20, featureSpecNewFileName, Set(scenarioA))
    checkSelection(8, 20, featureSpecNewFileName, Set(scenarioB))
    //on 'feature' word
    checkSelection(3, 7, featureSpecNewFileName, Set(scenarioA, scenarioB))
    //on feature name
    checkSelection(14, 15, featureSpecNewFileName, Set("Feature: Feature 2 Scenario: Scenario C"))
    //inside scenario
    checkSelection(5, 8, featureSpecNewFileName, Set(scenarioA))
    //tagged test
    checkSelection(24, 6, featureSpecNewFileName, Set("Feature: Feature 3 Scenario: Tagged"))
  }
}
