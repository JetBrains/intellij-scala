package org.jetbrains.plugins.scala.testingSupport.scalatest.finders

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest
import org.jetbrains.plugins.scala.testingSupport.scalatest.generators._
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestAstTransformer

/**
 * @author Roman.Shein
 * @since 10.02.2015.
 */
trait FindersApiTest extends IntegrationTest with FeatureSpecGenerator with FlatSpecGenerator with FreeSpecGenerator
with FreeSpecPathGenerator with FunSpecGenerator with FunSuiteGenerator with PropSpecGenerator with WordSpecGenerator {
  def checkSelection(lineNumber: Int, offset: Int, fileName: String, testNames: Set[String]) = {
    val location = createLocation(lineNumber, offset, fileName)
    val selection = new ScalaTestAstTransformer().testSelection(location)
    assert(selection != null)
    assert(selection.testNames().map(_.trim).toSet == testNames)
  }

  def testFeatureSpec() {
    addFeatureSpec()

    val scenarioA = "Feature: Feature 1 Scenario: Scenario A"
    val scenarioB = "Feature: Feature 1 Scenario: Scenario B"
    val fileName = "FeatureSpecTest.scala"

    //on 'scenario' word
    checkSelection(4, 8, fileName, Set(scenarioA))
    checkSelection(8, 8, fileName, Set(scenarioB))
    //on scenario name
    checkSelection(4, 20, fileName, Set(scenarioA))
    checkSelection(8, 20, fileName, Set(scenarioB))
    //on 'feature' word
    checkSelection(3, 7, fileName, Set(scenarioA, scenarioB))
    //on feature name
    checkSelection(14, 15, fileName, Set("Feature: Feature 2 Scenario: Scenario C"))
    //inside scenario
    checkSelection(5, 8, fileName, Set(scenarioA))
  }

  def testFlatSpec() {
    addFlatSpec()
    addBehaviorFlatSpec()

    val flatTestName1 = "A FlatSpecTest should be able to run single test"
    val flatTestName2 = "A FlatSpecTest should not run other tests"
    val fileName = "FlatSpecTest.scala"

    //object name
    checkSelection(4, 5, fileName, Set(flatTestName1))
    //'should' word
    checkSelection(4, 21, fileName, Set(flatTestName1))
    //behavior description
    checkSelection(4, 30, fileName, Set(flatTestName1))
    //'in' word
    checkSelection(4, 55, fileName, Set(flatTestName1))
    //'it' word
    checkSelection(16, 1, fileName, Set(flatTestName2))
  }

  def testBehaviorFlatSpec(){
    addBehaviorFlatSpec()

    val testNames = Set("FlatSpec should run scopes", "FlatSpec should do other stuff")
    val fileName = "BehaviorFlatSpec.scala"

    //'behavior' word
    checkSelection(3, 3, fileName, testNames)
    //'of' word
    checkSelection(3, 12, fileName, testNames)
    //object name
    checkSelection(3, 20, fileName, testNames)
    //specific test
    checkSelection(5, 8, fileName, Set("FlatSpec should run scopes"))
  }

  def testItFlatSpec(): Unit = {
    val fileName = "TestItFlatSpec.scala"
    addFileToProject(fileName,
      """
        |import org.scalatest._
        |
        |class TestItFlatSpec extends FlatSpec with GivenWhenThen {
        | it should "run test with correct name" in {
        | }
        |
        | "Test" should "be fine" in {}
        |
        | it should "change name" in {}
        |}
      """.stripMargin.trim())

    checkSelection(3, 10, fileName, Set("should run test with correct name"))

    checkSelection(6, 10, fileName, Set("Test should be fine"))

    checkSelection(8, 10, fileName, Set("Test should change name"))
  }

  //for now, there is no need to test path.FreeSpec separately: it and FreeSpec share the same finder
  def testFreeSpec() {
    addComplexFreeSpec()

    val fileName = "ComplexFreeSpec.scala"
    val testName1 = "A ComplexFreeSpec Outer scope 1 Inner scope 1"
    val testName2 = "A ComplexFreeSpec Outer scope 2 Inner test"
    val testName3 = "A ComplexFreeSpec Outer scope 2 Inner scope 2 Another innermost scope"
    val ignoredTestName = "A ComplexFreeSpec Outer scope 2 Inner scope 2 Innermost scope"

    //outermost close
    checkSelection(3, 10, fileName, Set(testName1, testName2, testName3))
    //just a nested scope
    checkSelection(4, 12, fileName, Set(testName1))
    //a test scope (scope with 'in')
    checkSelection(5, 11, fileName, Set(testName1))
    //a dash
    checkSelection(4, 20, fileName, Set(testName1))
    //a whitespace
    checkSelection(4, 21, fileName, Set(testName1))
    //'in' word
    checkSelection(5, 23, fileName, Set(testName1))
    //TODO: ignored tests are not processed by the finder, even when left-clicking exactly the ignored test.
    // So finders do not provide a testName and our manual search does. It is a bit inconsistent.
    //an ignored test scope
    checkSelection(25, 13, fileName, Set())
    //'ignore' word
    checkSelection(25, 27, fileName, Set())
    //different depth scopes
    checkSelection(10, 10, fileName, Set(testName2, testName3))
  }

  def testFunSpec() {
    addFunSpec()

    val fileName = "FunSpecTest.scala"

    val testName1 = "FunSpecTest should launch single test"
    val testName2 = "FunSpecTest should not launch other tests"

    //'describe' word
    checkSelection(3, 5, fileName, Set(testName1, testName2))
    //described test name
    checkSelection(3, 20, fileName, Set(testName1, testName2))
    //'it' word
    checkSelection(4, 5, fileName, Set(testName1))
    //test name
    checkSelection(4, 15, fileName, Set(testName1))
    //inside test
    checkSelection(9, 1, fileName, Set(testName2))
    checkSelection(9, 15, fileName, Set(testName2))
  }

  def testFunSuite(){
    addFunSuite()

    val fileName = "FunSuiteTest.scala"
    val testName1 = "should not run other tests"

    //'test' word
    checkSelection(4, 4, fileName, Set(testName1))
    //test name
    checkSelection(4, 10, fileName, Set(testName1))
    //inside test
    checkSelection(5, 10, fileName, Set(testName1))
  }

  def testPropSpec() {
    addPropSpec()

    val fileName = "PropSpecTest.scala"
    val testName1 = "Single tests should run"

    //'property' word
    checkSelection(4, 5, fileName, Set(testName1))
    //test name
    checkSelection(4, 15, fileName, Set(testName1))
    //inside test
    checkSelection(5, 10, fileName, Set(testName1))
  }

  def testWordSpec() {
    addWordSpec()

    val fileName = "WordSpecTest.scala"
    val testName1 = "WordSpecTest should Run single test"
    val testName2 = "WordSpecTest should ignore other tests"

    //outer scope
    checkSelection(3, 5, fileName, Set(testName1, testName2))
    //'should' word
    checkSelection(3, 20, fileName, Set(testName1, testName2))
    //inner scope
    checkSelection(4, 10, fileName, Set(testName1))
    //'in' word
    checkSelection(8, 26, fileName, Set(testName2))
  }
}
