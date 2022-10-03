package org.jetbrains.plugins.scala.testingSupport.scalatest.base

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators._

trait ScalaTestWholeSuiteTest extends ScalaTestTestCase
  with FeatureSpecGenerator
  with FlatSpecGenerator
  with FreeSpecGenerator
  with FunSpecGenerator
  with FunSuiteGenerator
  with PropSpecGenerator
  with WordSpecGenerator {

  protected val featureSpecTestPaths = Seq(
    TestNodePath("[root]", featureSpecClassName, "Feature: Feature 1", "Scenario: Scenario A"),
    TestNodePath("[root]", featureSpecClassName, "Feature: Feature 1", "Scenario: Scenario B"),
    TestNodePath("[root]", featureSpecClassName, "Feature: Feature 2", "Scenario: Scenario C")
  )
  protected val flatSpecTestPaths = Seq(
    TestNodePath("[root]", flatSpecClassName, "A FlatSpecTest", "should be able to run single test"),
    TestNodePath("[root]", flatSpecClassName, "A FlatSpecTest", "should not run other tests")
  )
  protected val freeSpecTestPaths = Seq(
    TestNodePath("[root]", freeSpecClassName, "A FreeSpecTest", "should be able to run single tests"),
    TestNodePath("[root]", freeSpecClassName, "A FreeSpecTest", "should not run tests that are not selected")
  )
  protected val funSpecTestPaths = Seq(
    TestNodePath("[root]", funSpecClassName, "FunSpecTest", "should launch single test"),
    TestNodePath("[root]", funSpecClassName, "FunSpecTest", "should not launch other tests"),
    TestNodePath("[root]", funSpecClassName, "OtherScope", "is here")
  )
  protected val funSuiteTestPaths = Seq(
    TestNodePath("[root]", funSuiteClassName, "should run single test"),
    TestNodePath("[root]", funSuiteClassName, "should not run other tests")
  )
  protected val propSpecTestPaths = Seq(
    TestNodePath("[root]", propSpecClassName, "Single tests should run"),
    TestNodePath("[root]", propSpecClassName, "other test should not run")
  )
  protected val wordSpecTestPaths = Seq(
    TestNodePath("[root]", wordSpecClassName, "WordSpecTest", "Run single test"),
    TestNodePath("[root]", wordSpecClassName, "WordSpecTest", "ignore other tests"), TestNodePath("[root]", "WordSpecTest", "outer", "inner")
  )

  def testFeatureSpec(): Unit =
    runTestByLocation(loc(featureSpecFileName, 2, 10),
      assertConfigAndSettings(_, featureSpecClassName),
      root => assertResultTreeHasExactNamedPaths(root)(featureSpecTestPaths)
    )

  def testFlatSpec(): Unit =
    runTestByLocation(loc(flatSpecFileName, 2, 10),
      assertConfigAndSettings(_, flatSpecClassName),
      root => assertResultTreeHasExactNamedPaths(root)(flatSpecTestPaths)
    )

  def testFreeSpec(): Unit =
    runTestByLocation(loc(freeSpecFileName, 2, 10),
      assertConfigAndSettings(_, freeSpecClassName),
      root => assertResultTreeHasExactNamedPaths(root)(freeSpecTestPaths)
    )

  def testFunSpec(): Unit =
    runTestByLocation(loc(funSpecFileName, 2, 10),
      assertConfigAndSettings(_, funSpecClassName),
      root => assertResultTreeHasExactNamedPaths(root)(funSpecTestPaths)
    )

  def testFunSuite(): Unit =
    runTestByLocation(loc(funSuiteFileName, 2, 10),
      assertConfigAndSettings(_, funSuiteClassName),
      root => assertResultTreeHasExactNamedPaths(root)(funSuiteTestPaths)
    )

  def testPropSpec(): Unit =
    runTestByLocation(loc(propSpecFileName, 2, 10),
      assertConfigAndSettings(_, propSpecClassName),
      root => assertResultTreeHasExactNamedPaths(root)(propSpecTestPaths)
    )

  def testWordSpec(): Unit =
    runTestByLocation(loc(wordSpecFileName, 2, 10),
      assertConfigAndSettings(_, wordSpecClassName),
      root => assertResultTreeHasExactNamedPaths(root)(wordSpecTestPaths)
    )
}
