package org.jetbrains.plugins.scala.testingSupport.scalatest.base

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
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
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", featureSpecClassName, "Feature: Feature 1", "Scenario: Scenario A"),
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", featureSpecClassName, "Feature: Feature 1", "Scenario: Scenario B"),
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", featureSpecClassName, "Feature: Feature 2", "Scenario: Scenario C"),
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", featureSpecClassName, "Feature: Feature 3", "Scenario: Tagged"),
  )
  protected val flatSpecTestPaths = Seq(
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", flatSpecClassName, "A FlatSpecTest", "should be able to run single test"),
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", flatSpecClassName, "A FlatSpecTest", "should not run other tests"),
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", flatSpecClassName, "A FlatSpecTest", "should run tagged tests"),
  )
  protected val freeSpecTestPaths = Seq(
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", freeSpecClassName, "A FreeSpecTest", "should be able to run single tests"),
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", freeSpecClassName, "A FreeSpecTest", "should not run tests that are not selected"),
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", freeSpecClassName, "A FreeSpecTest", "can be tagged"),
  )
  protected val funSpecTestPaths = Seq(
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", funSpecClassName, "FunSpecTest", "should launch single test"),
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", funSpecClassName, "FunSpecTest", "should not launch other tests"),
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", funSpecClassName, "OtherScope", "is here"),
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", funSpecClassName, "taggedScope", "is tagged"),
  )
  protected val funSuiteTestPaths = Seq(
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", funSuiteClassName, "should run single test"),
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", funSuiteClassName, "should not run other tests"),
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", funSuiteClassName, "tagged"),
  )
  protected val propSpecTestPaths = Seq(
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", propSpecClassName, "Single tests should run"),
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", propSpecClassName, "other test should not run"),
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", propSpecClassName, "tagged"),
  )
  protected val wordSpecTestPaths = Seq(
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", wordSpecClassName, "WordSpecTest", "Run single test"),
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", wordSpecClassName, "WordSpecTest", "ignore other tests"),
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", wordSpecClassName, "outer", "inner"),
    TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", wordSpecClassName, "tagged", "be tagged"),
  )

  def testFeatureSpec(): Unit =
    runTestByLocation(loc(featureSpecFileName, 2, 10),
      assertConfigAndSettings(_, featureSpecClassName),
      root => assertResultTreePathsEqualsUnordered(root)(featureSpecTestPaths)
    )

  def testFlatSpec(): Unit =
    runTestByLocation(loc(flatSpecFileName, 2, 10),
      assertConfigAndSettings(_, flatSpecClassName),
      root => assertResultTreePathsEqualsUnordered(root)(flatSpecTestPaths)
    )

  def testFreeSpec(): Unit =
    runTestByLocation(loc(freeSpecFileName, 2, 10),
      assertConfigAndSettings(_, freeSpecClassName),
      root => assertResultTreePathsEqualsUnordered(root)(freeSpecTestPaths)
    )

  def testFunSpec(): Unit =
    runTestByLocation(loc(funSpecFileName, 2, 10),
      assertConfigAndSettings(_, funSpecClassName),
      root => assertResultTreePathsEqualsUnordered(root)(funSpecTestPaths)
    )

  def testFunSuite(): Unit =
    runTestByLocation(loc(funSuiteFileName, 2, 10),
      assertConfigAndSettings(_, funSuiteClassName),
      root => assertResultTreePathsEqualsUnordered(root)(funSuiteTestPaths)
    )

  def testPropSpec(): Unit =
    runTestByLocation(loc(propSpecFileName, 2, 10),
      assertConfigAndSettings(_, propSpecClassName),
      root => assertResultTreePathsEqualsUnordered(root)(propSpecTestPaths)
    )

  def testWordSpec(): Unit =
    runTestByLocation(loc(wordSpecFileName, 2, 10),
      assertConfigAndSettings(_, wordSpecClassName),
      root => assertResultTreePathsEqualsUnordered(root)(wordSpecTestPaths)
    )
}
