package org.jetbrains.plugins.scala.testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators._

/**
 * @author Roman.Shein
 * @since 11.02.2015.
 */
trait ScalaTestWholeSuiteTest extends FeatureSpecGenerator with FlatSpecGenerator with FreeSpecGenerator
with FunSpecGenerator with FunSuiteGenerator with PropSpecGenerator with WordSpecGenerator {
  val featureSpecTestPaths = List(List("[root]", "FeatureSpecTest", "Feature: Feature 1", "Scenario: Scenario A"),
    List("[root]", "FeatureSpecTest", "Feature: Feature 1", "Scenario: Scenario B"),
    List("[root]", "FeatureSpecTest", "Feature: Feature 2", "Scenario: Scenario C"))
  val flatSpecTestPaths = List(List("[root]", "FlatSpecTest", "A FlatSpecTest", "should be able to run single test"),
    List("[root]", "FlatSpecTest", "A FlatSpecTest", "should not run other tests"))
  val freeSpecTestPaths = List(List("[root]", "FreeSpecTest", "A FreeSpecTest", "should be able to run single tests"),
    List("[root]", "FreeSpecTest", "A FreeSpecTest", "should not run tests that are not selected"))
  val funSpecTestPaths = List(List("[root]", "FunSpecTest", "FunSpecTest", "should launch single test"),
    List("[root]", "FunSpecTest", "FunSpecTest", "should not launch other tests"))
  val funSuiteTestPaths = List(List("[root]", "FunSuiteTest", "should run single test"),
    List("[root]", "FunSuiteTest", "should not run other tests"))
  val propSpecTestPaths = List(List("[root]", "PropSpecTest", "Single tests should run"),
    List("[root]", "PropSpecTest", "other test should not run"))
  val wordSpecTestPaths = List(List("[root]", "WordSpecTest", "WordSpecTest", "Run single test"),
    List("[root]", "WordSpecTest", "WordSpecTest", "ignore other tests"))

  def testFeatureSpec() {
    addFeatureSpec()

    runTestByLocation(2, 10, "FeatureSpecTest.scala",
      checkConfigAndSettings(_, "FeatureSpecTest"),
      root => featureSpecTestPaths.forall(checkResultTreeHasExactNamedPath(root, _:_*))
//        checkResultTreeHasExactNamedPath(root, "[root]", "FeatureSpecTest", "Feature: Feature 1", "Scenario: Scenario A") &&
//          checkResultTreeHasExactNamedPath(root, "[root]", "FeatureSpecTest", "Feature: Feature 1", "Scenario: Scenario B") &&
//          checkResultTreeHasExactNamedPath(root, "[root]", "FeatureSpecTest", "Feature: Feature 2", "Scenario: Scenario C")
    )
  }

  def testFlatSpec() {
    addFlatSpec()

    runTestByLocation(2, 10, "FlatSpecTest.scala",
      checkConfigAndSettings(_, "FlatSpecTest"),
      root => flatSpecTestPaths.forall(checkResultTreeHasExactNamedPath(root, _:_*))
//        checkResultTreeHasExactNamedPath(root, "[root]", "FlatSpecTest", "A FlatSpecTest", "should be able to run single test") &&
//          checkResultTreeHasExactNamedPath(root, "[root]", "FlatSpecTest", "A FlatSpecTest", "should not run other tests")
    )
  }

  def testFreeSpec() {
    addFreeSpec()

    runTestByLocation(2, 10, "FreeSpecTest.scala",
      checkConfigAndSettings(_, "FreeSpecTest"),
      root => freeSpecTestPaths.forall(checkResultTreeHasExactNamedPath(root, _:_*))
//        checkResultTreeHasExactNamedPath(root, "[root]", "FreeSpecTest", "A FreeSpecTest", "should be able to run single tests") &&
//          checkResultTreeHasExactNamedPath(root, "[root]", "FreeSpecTest", "A FreeSpecTest", "should not run tests that are not selected")
    )
  }

  def testFunSpec() {
    addFunSpec()

    runTestByLocation(2, 10, "FunSpecTest.scala",
      checkConfigAndSettings(_, "FunSpecTest"),
      root => funSpecTestPaths.forall(checkResultTreeHasExactNamedPath(root, _:_*))
//        checkResultTreeHasExactNamedPath(root, "[root]", "FunSpecTest", "FunSpecTest", "should launch single test") &&
//          checkResultTreeHasExactNamedPath(root, "[root]", "FunSpecTest", "FunSpecTest", "should not launch other tests")
    )
  }

  def testFunSuite() {
    addFunSuite()

    runTestByLocation(2, 10, "FunSuiteTest.scala",
      checkConfigAndSettings(_, "FunSuiteTest"),
      root => funSuiteTestPaths.forall(checkResultTreeHasExactNamedPath(root, _:_*))
//        checkResultTreeHasExactNamedPath(root, "[root]", "FunSuiteTest", "should run single test") &&
//          checkResultTreeHasExactNamedPath(root, "[root]", "FunSuiteTest", "should not run other tests")
    )
  }

  def testPropSpec() {
    addPropSpec()

    runTestByLocation(2, 10, "PropSpecTest.scala",
      checkConfigAndSettings(_, "PropSpecTest"),
      root => propSpecTestPaths.forall(checkResultTreeHasExactNamedPath(root, _:_*))
//        checkResultTreeHasExactNamedPath(root, "[root]", "PropSpecTest", "Single tests should run") &&
//          checkResultTreeHasExactNamedPath(root, "[root]", "PropSpecTest", "other test should not run")
    )
  }

  def testWordSpec() {
    addWordSpec()

    runTestByLocation(2, 10, "WordSpecTest.scala",
      checkConfigAndSettings(_, "WordSpecTest"),
      root => wordSpecTestPaths.forall(checkResultTreeHasExactNamedPath(root, _:_*))
//        checkResultTreeHasExactNamedPath(root, "[root]", "WordSpecTest", "WordSpecTest", "Run single test") &&
//          checkResultTreeHasExactNamedPath(root, "[root]", "WordSpecTest", "WordSpecTest", "ignore other tests")
    )
  }
}
