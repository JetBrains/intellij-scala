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

  val featureSpecTestPaths = Seq(
    Seq("[root]", featureSpecClassName, "Feature: Feature 1", "Scenario: Scenario A"),
    Seq("[root]", featureSpecClassName, "Feature: Feature 1", "Scenario: Scenario B"),
    Seq("[root]", featureSpecClassName, "Feature: Feature 2", "Scenario: Scenario C")
  )
  val flatSpecTestPaths = Seq(
    Seq("[root]", flatSpecClassName, "A FlatSpecTest", "should be able to run single test"),
    Seq("[root]", flatSpecClassName, "A FlatSpecTest", "should not run other tests")
  )
  val freeSpecTestPaths = Seq(
    Seq("[root]", freeSpecClassName, "A FreeSpecTest", "should be able to run single tests"),
    Seq("[root]", freeSpecClassName, "A FreeSpecTest", "should not run tests that are not selected")
  )
  val funSpecTestPaths = Seq(
    Seq("[root]", funSpecClassName, "FunSpecTest", "should launch single test"),
    Seq("[root]", funSpecClassName, "FunSpecTest", "should not launch other tests"),
    Seq("[root]", funSpecClassName, "OtherScope", "is here")
  )
  val funSuiteTestPaths = Seq(
    Seq("[root]", funSuiteClassName, "should run single test"),
    Seq("[root]", funSuiteClassName, "should not run other tests")
  )
  val propSpecTestPaths = Seq(
    Seq("[root]", propSpecClassName, "Single tests should run"),
    Seq("[root]", propSpecClassName, "other test should not run")
  )
  val wordSpecTestPaths = Seq(
    Seq("[root]", wordSpecClassName, "WordSpecTest", "Run single test"),
    Seq("[root]", wordSpecClassName, "WordSpecTest", "ignore other tests"), Seq("[root]", "WordSpecTest", "outer", "inner")
  )

  def testFeatureSpec(): Unit =
    runTestByLocation2(2, 10, featureSpecFileName,
      assertConfigAndSettings(_, featureSpecClassName),
      root => assertResultTreeHasExactNamedPaths(root)(featureSpecTestPaths)
    )

  def testFlatSpec(): Unit =
    runTestByLocation2(2, 10, flatSpecFileName,
      assertConfigAndSettings(_, flatSpecClassName),
      root => assertResultTreeHasExactNamedPaths(root)(flatSpecTestPaths)
    )

  def testFreeSpec(): Unit =
    runTestByLocation2(2, 10, freeSpecFileName,
      assertConfigAndSettings(_, freeSpecClassName),
      root => assertResultTreeHasExactNamedPaths(root)(freeSpecTestPaths)
    )

  def testFunSpec(): Unit =
    runTestByLocation2(2, 10, funSpecFileName,
      assertConfigAndSettings(_, funSpecClassName),
      root => assertResultTreeHasExactNamedPaths(root)(funSpecTestPaths)
    )

  def testFunSuite(): Unit =
    runTestByLocation2(2, 10, funSuiteFileName,
      assertConfigAndSettings(_, funSuiteClassName),
      root => assertResultTreeHasExactNamedPaths(root)(funSuiteTestPaths)
    )

  def testPropSpec(): Unit =
    runTestByLocation2(2, 10, propSpecFileName,
      assertConfigAndSettings(_, propSpecClassName),
      root => assertResultTreeHasExactNamedPaths(root)(propSpecTestPaths)
    )

  def testWordSpec(): Unit =
    runTestByLocation2(2, 10, wordSpecFileName,
      assertConfigAndSettings(_, wordSpecClassName),
      root => assertResultTreeHasExactNamedPaths(root)(wordSpecTestPaths)
    )
}
