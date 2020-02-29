package org.jetbrains.plugins.scala.testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators._

trait ScalaTestWholeSuiteTest extends ScalaTestTestCase
  with FeatureSpecGenerator
  with FlatSpecGenerator
  with FreeSpecGenerator
  with FunSpecGenerator
  with FunSuiteGenerator
  with PropSpecGenerator
  with WordSpecGenerator {

  val featureSpecTestPaths = List(
    List("[root]", featureSpecClassName, "Feature: Feature 1", "Scenario: Scenario A"),
    List("[root]", featureSpecClassName, "Feature: Feature 1", "Scenario: Scenario B"),
    List("[root]", featureSpecClassName, "Feature: Feature 2", "Scenario: Scenario C")
  )
  val flatSpecTestPaths = List(
    List("[root]", flatSpecClassName, "A FlatSpecTest", "should be able to run single test"),
    List("[root]", flatSpecClassName, "A FlatSpecTest", "should not run other tests")
  )
  val freeSpecTestPaths = List(
    List("[root]", freeSpecClassName, "A FreeSpecTest", "should be able to run single tests"),
    List("[root]", freeSpecClassName, "A FreeSpecTest", "should not run tests that are not selected")
  )
  val funSpecTestPaths = List(
    List("[root]", funSpecClassName, "FunSpecTest", "should launch single test"),
    List("[root]", funSpecClassName, "FunSpecTest", "should not launch other tests"),
    List("[root]", funSpecClassName, "OtherScope", "is here")
  )
  val funSuiteTestPaths = List(
    List("[root]", funSuiteClassName, "should run single test"),
    List("[root]", funSuiteClassName, "should not run other tests")
  )
  val propSpecTestPaths = List(
    List("[root]", propSpecClassName, "Single tests should run"),
    List("[root]", propSpecClassName, "other test should not run")
  )
  val wordSpecTestPaths = List(
    List("[root]", wordSpecClassName, "WordSpecTest", "Run single test"),
    List("[root]", wordSpecClassName, "WordSpecTest", "ignore other tests"), List("[root]", "WordSpecTest", "outer", "inner")
  )

  def testFeatureSpec(): Unit =
    runTestByLocation(2, 10, featureSpecFileName,
      checkConfigAndSettings(_, featureSpecClassName),
      root => featureSpecTestPaths.forall(checkResultTreeHasExactNamedPath(root, _:_*))
    )

  def testFlatSpec(): Unit =
    runTestByLocation(2, 10, flatSpecFileName,
      checkConfigAndSettings(_, flatSpecClassName),
      root => flatSpecTestPaths.forall(checkResultTreeHasExactNamedPath(root, _:_*))
    )

  def testFreeSpec(): Unit =
    runTestByLocation(2, 10, freeSpecFileName,
      checkConfigAndSettings(_, freeSpecClassName),
      root => freeSpecTestPaths.forall(checkResultTreeHasExactNamedPath(root, _:_*))
    )

  def testFunSpec(): Unit =
    runTestByLocation(2, 10, funSpecFileName,
      checkConfigAndSettings(_, funSpecClassName),
      root => funSpecTestPaths.forall(checkResultTreeHasExactNamedPath(root, _:_*))
    )

  def testFunSuite(): Unit =
    runTestByLocation(2, 10, funSuiteFileName,
      checkConfigAndSettings(_, funSuiteClassName),
      root => funSuiteTestPaths.forall(checkResultTreeHasExactNamedPath(root, _:_*))
    )

  def testPropSpec(): Unit =
    runTestByLocation(2, 10, propSpecFileName,
      checkConfigAndSettings(_, propSpecClassName),
      root => propSpecTestPaths.forall(checkResultTreeHasExactNamedPath(root, _:_*))
    )

  def testWordSpec(): Unit =
    runTestByLocation(2, 10, wordSpecFileName,
      checkConfigAndSettings(_, wordSpecClassName),
      root => wordSpecTestPaths.forall(checkResultTreeHasExactNamedPath(root, _:_*))
    )
}
