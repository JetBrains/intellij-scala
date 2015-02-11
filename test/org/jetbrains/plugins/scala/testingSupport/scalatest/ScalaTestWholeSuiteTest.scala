package org.jetbrains.plugins.scala.testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators._

/**
 * @author Roman.Shein
 * @since 11.02.2015.
 */
trait ScalaTestWholeSuiteTest extends FeatureSpecGenerator with FlatSpecGenerator with FreeSpecGenerator
with FunSpecGenerator with FunSuiteGenerator with PropSpecGenerator with WordSpecGenerator {
  def testFeatureSpec() {
    addFeatureSpec()

    runTestByLocation(2, 10, "FeatureSpecTest.scala",
      checkConfigAndSettings(_, "FeatureSpecTest"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "FeatureSpecTest", "Feature: Feature 1", "Scenario: Scenario A") &&
          checkResultTreeHasExactNamedPath(root, "[root]", "FeatureSpecTest", "Feature: Feature 1", "Scenario: Scenario B") &&
          checkResultTreeHasExactNamedPath(root, "[root]", "FeatureSpecTest", "Feature: Feature 2", "Scenario: Scenario C")
    )
  }

  def testFlatSpec() {
    addFlatSpec()

    runTestByLocation(2, 10, "FlatSpecTest.scala",
      checkConfigAndSettings(_, "FlatSpecTest"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "FlatSpecTest", "A FlatSpecTest", "should be able to run single test") &&
          checkResultTreeHasExactNamedPath(root, "[root]", "FlatSpecTest", "A FlatSpecTest", "should not run other tests")
    )
  }

  def testFreeSpec() {
    addFreeSpec()

    runTestByLocation(2, 10, "FreeSpecTest.scala",
      checkConfigAndSettings(_, "FreeSpecTest"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "FreeSpecTest", "A FreeSpecTest", "should be able to run single tests") &&
          checkResultTreeHasExactNamedPath(root, "[root]", "FreeSpecTest", "A FreeSpecTest", "should not run tests that are not selected")
    )
  }

  def testFunSpec() {
    addFunSpec()

    runTestByLocation(2, 10, "FunSpecTest.scala",
      checkConfigAndSettings(_, "FunSpecTest"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "FunSpecTest", "FunSpecTest", "should launch single test") &&
          checkResultTreeHasExactNamedPath(root, "[root]", "FunSpecTest", "FunSpecTest", "should not launch other tests")
    )
  }

  def testFunSuite() {
    addFunSuite()

    runTestByLocation(2, 10, "FunSuiteTest.scala",
      checkConfigAndSettings(_, "FunSuiteTest"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "FunSuiteTest", "should run single test") &&
          checkResultTreeHasExactNamedPath(root, "[root]", "FunSuiteTest", "should not run other tests")
    )
  }

  def testPropSpec() {
    addPropSpec()

    runTestByLocation(2, 10, "PropSpecTest.scala",
      checkConfigAndSettings(_, "PropSpecTest"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "PropSpecTest", "Single tests should run") &&
          checkResultTreeHasExactNamedPath(root, "[root]", "PropSpecTest", "other test should not run")
    )
  }

  def testWordSpec() {
    addWordSpec()

    runTestByLocation(2, 10, "WordSpecTest.scala",
      checkConfigAndSettings(_, "WordSpecTest"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "WordSpecTest", "WordSpecTest", "Run single test") &&
          checkResultTreeHasExactNamedPath(root, "[root]", "WordSpecTest", "WordSpecTest", "ignore other tests")
    )
  }
}
