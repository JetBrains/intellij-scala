package org.jetbrains.plugins.scala.testingSupport.scalatest.finders

import com.intellij.testFramework.EdtTestUtil
import org.jetbrains.plugins.scala.testingSupport.scalatest.generators._
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestAstTransformer
import org.junit.Assert.{assertEquals, assertNotNull}
import org.scalatest.finders.Selection

/**
  * @author Roman.Shein
  * @since 10.02.2015.
  */
trait FindersApiTest
  extends FeatureSpecGenerator
    with FlatSpecGenerator
    with FreeSpecGenerator
    with FreeSpecPathGenerator
    with FunSpecGenerator
    with FunSuiteGenerator
    with PropSpecGenerator
    with WordSpecGenerator {

  def checkSelection(lineNumber: Int, offset: Int, fileName: String, testNames: Set[String]): Unit = {
    val location = createLocation(lineNumber, offset, fileName)
    var selection: Selection = null
    EdtTestUtil.runInEdtAndWait { () =>
      selection = ScalaTestAstTransformer.testSelection(location)
    }
    assertNotNull(selection)
    assertEquals(testNames, selection.testNames().map(_.trim).toSet)
  }

  def testFeatureSpec() {
    val scenarioA = "Feature: Feature 1 Scenario: Scenario A"
    val scenarioB = "Feature: Feature 1 Scenario: Scenario B"

    //on 'scenario' word
    checkSelection(4, 8, featureSpecFileName, Set(scenarioA))
    checkSelection(8, 8, featureSpecFileName, Set(scenarioB))
    //on scenario name
    checkSelection(4, 20, featureSpecFileName, Set(scenarioA))
    checkSelection(8, 20, featureSpecFileName, Set(scenarioB))
    //on 'feature' word
    checkSelection(3, 7, featureSpecFileName, Set(scenarioA, scenarioB))
    //on feature name
    checkSelection(14, 15, featureSpecFileName, Set("Feature: Feature 2 Scenario: Scenario C"))
    //inside scenario
    checkSelection(5, 8, featureSpecFileName, Set(scenarioA))
    //tagged test
    checkSelection(24, 6, featureSpecFileName, Set("Feature: Feature 3 Scenario: Tagged"))
  }

  def testFlatSpec() {

    val flatTestName1 = "A FlatSpecTest should be able to run single test"
    val flatTestName2 = "A FlatSpecTest should not run other tests"

    //object name
    checkSelection(4, 5, flatSpecFileName, Set(flatTestName1))
    //'should' word
    checkSelection(4, 21, flatSpecFileName, Set(flatTestName1))
    //behavior description
    checkSelection(4, 30, flatSpecFileName, Set(flatTestName1))
    //'in' word
    checkSelection(4, 55, flatSpecFileName, Set(flatTestName1))
    //'it' word
    checkSelection(16, 1, flatSpecFileName, Set(flatTestName2))
    //tagged test
    checkSelection(19, 3, flatSpecFileName, Set("A FlatSpecTest should run tagged tests"))
  }

  def testFlatSpec_WithBehavior() {
    val testName1 = "FlatSpec should run scopes"
    val testName2 = "FlatSpec should do other stuff"
    val testName3 = "FlatSpec should tag"

    val testNames = Set(testName1, testName2, testName3)

    //'behavior' word
    checkSelection(3, 3, behaviourFlatFileName, testNames)
    //'of' word
    checkSelection(3, 12, behaviourFlatFileName, testNames)
    //object name
    checkSelection(3, 20, behaviourFlatFileName, testNames)

    //specific test, 'it' word
    checkSelection(5, 3, behaviourFlatFileName, Set(testName1))
    //specific test, should
    checkSelection(5, 8, behaviourFlatFileName, Set(testName1))
    //specific test, object name
    checkSelection(5, 15, behaviourFlatFileName, Set(testName1))
    //specific test, 'in' word
    checkSelection(5, 30, behaviourFlatFileName, Set(testName1))
    //specific test, test body
    checkSelection(6, 1, behaviourFlatFileName, Set(testName1))

    //tagged test, 'it' word
    checkSelection(13, 3, behaviourFlatFileName, Set(testName3))
    //tagged test, should word
    checkSelection(13, 7, behaviourFlatFileName, Set(testName3))
    //tagged test, object name
    checkSelection(13, 14, behaviourFlatFileName, Set(testName3))
    //tagged test, taggedAs
    checkSelection(13, 20, behaviourFlatFileName, Set(testName3))
    //tagged test, tag name
    checkSelection(13, 30, behaviourFlatFileName, Set(testName3))
    //tagged test, 'in' word
    checkSelection(13, 41, behaviourFlatFileName, Set(testName3))
    //tagged test, test body
    checkSelection(14, 2, behaviourFlatFileName, Set(testName3))
  }

  def testFlatSpec_ItOnly(): Unit = {
    val testName1 = "should run test with correct name"
    checkSelection(3, 3, testItFlatFileName, Set(testName1))
    checkSelection(3, 7, testItFlatFileName, Set(testName1))
    checkSelection(3, 10, testItFlatFileName, Set(testName1))
    checkSelection(3, 41, testItFlatFileName, Set(testName1))
    checkSelection(4, 1, testItFlatFileName, Set(testName1))

    checkSelection(9, 10, testItFlatFileName, Set("Test should be fine"))

    checkSelection(11, 10, testItFlatFileName, Set("Test should change name"))

    checkSelection(6, 5, testItFlatFileName, Set("should tag"))
  }

  //for now, there is no need to test path.FreeSpec separately: it and FreeSpec share the same finder
  def testFreeSpec() {
    val testName1 = "A ComplexFreeSpec Outer scope 1 Inner scope 1"
    val testName2 = "A ComplexFreeSpec Outer scope 2 Inner test"
    val testName3 = "A ComplexFreeSpec Outer scope 2 Inner scope 2 Another innermost scope"
    val ignoredTestName = "A ComplexFreeSpec Outer scope 2 Inner scope 2 Innermost scope"

    //outermost close
    checkSelection(3, 10, complexFreeSpecFileName, Set(testName1, testName2, testName3))
    //just a nested scope
    checkSelection(4, 12, complexFreeSpecFileName, Set(testName1))
    //a test scope (scope with 'in')
    checkSelection(5, 11, complexFreeSpecFileName, Set(testName1))
    //a dash
    checkSelection(4, 20, complexFreeSpecFileName, Set(testName1))
    //a whitespace
    checkSelection(4, 21, complexFreeSpecFileName, Set(testName1))
    //'in' word
    checkSelection(5, 23, complexFreeSpecFileName, Set(testName1))
    //TODO: ignored tests are not processed by the finder, even when left-clicking exactly the ignored test.
    // So finders do not provide a testName and our manual search does. It is a bit inconsistent.
    //an ignored test scope
    checkSelection(25, 13, complexFreeSpecFileName, Set())
    //'ignore' word
    checkSelection(25, 27, complexFreeSpecFileName, Set())
    //different depth scopes
    checkSelection(10, 10, complexFreeSpecFileName, Set(testName2, testName3))
    //tagged test
    checkSelection(12, 10, freeSpecFileName, Set("A FreeSpecTest can be tagged"))
  }

  def testFunSpec() {
    val testName1 = "FunSpecTest should launch single test"
    val testName2 = "FunSpecTest should not launch other tests"

    //'describe' word
    checkSelection(3, 5, funSpecFileName, Set(testName1, testName2))
    //described test name
    checkSelection(3, 20, funSpecFileName, Set(testName1, testName2))
    //'it' word
    checkSelection(4, 5, funSpecFileName, Set(testName1))
    //test name
    checkSelection(4, 15, funSpecFileName, Set(testName1))
    //inside test
    checkSelection(9, 1, funSpecFileName, Set(testName2))
    checkSelection(9, 15, funSpecFileName, Set(testName2))
    //tagged test
    checkSelection(20, 10, funSpecFileName, Set("taggedScope is tagged"))
  }

  def testFunSuite() {
    val testName1 = "should not run other tests"

    //'test' word
    checkSelection(4, 4, funSuiteFileName, Set(testName1))
    //test name
    checkSelection(4, 10, funSuiteFileName, Set(testName1))
    //inside test
    checkSelection(5, 10, funSuiteFileName, Set(testName1))
    //tagged test
    checkSelection(12, 10, funSuiteFileName, Set("tagged"))
  }

  def testPropSpec() {
    val testName1 = "Single tests should run"

    //'property' word
    checkSelection(4, 5, propSpecFileName, Set(testName1))
    //test name
    checkSelection(4, 15, propSpecFileName, Set(testName1))
    //inside test
    checkSelection(5, 10, propSpecFileName, Set(testName1))
    //tagged test
    checkSelection(12, 10, propSpecFileName, Set("tagged"))
  }

  def testWordSpec() {
    val testName1 = "WordSpecTest should Run single test"
    val testName2 = "WordSpecTest should ignore other tests"

//    //outer scope
//    checkSelection(3, 5, wordSpecFileName, Set(testName1, testName2))
//    //'should' word
//    checkSelection(3, 20, wordSpecFileName, Set(testName1, testName2))
//    //inner scope
//    checkSelection(4, 10, wordSpecFileName, Set(testName1))
//    //'in' word
//    checkSelection(8, 26, wordSpecFileName, Set(testName2))
    //tagged test
    checkSelection(20, 10, wordSpecFileName, Set("tagged should be tagged"))
  }
}
