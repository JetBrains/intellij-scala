package org.jetbrains.plugins.scala
package testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.ScalaTestingTestCase
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestConfigurationProducer
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration

/**
 * @author Roman.Shein
 *         Date: 03.03.14
 */
abstract class ScalaTestSingleTestTest extends ScalaTestTestCase {

  def testFeatureSpec() {
    addFileToProject("FeatureSpecTest.scala",
      """
        |import org.scalatest._
        |
        |class FeatureSpecTest extends FeatureSpec with GivenWhenThen {
        | feature("Feature 1") {
        |   scenario("Scenario A") {
        |    Given("A")
        |    print(">>TEST: OK<<")
        |   }
        |   scenario("Scenario B") {
        |    Given("B")
        |    print(">>TEST: 1-B-B<<")
        |   }
        | }
        |
        | feature("Feature 2") {
        |   scenario("Scenario C") {
        |    Given("C")
        |    print(">>TEST: 2-C-C<<")
        |   }
        | }
        |}
      """.stripMargin.trim()
    )

    runTestByLocation(5, 7, "FeatureSpecTest.scala",
      checkConfigAndSettings(_, "FeatureSpecTest", "Feature: Feature 1 Scenario: Scenario A"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "FeatureSpecTest", "Feature: Feature 1", "Scenario: Scenario A") &&
      checkResultTreeDoesNotHaveNodes(root, "Scenario: Scenario B"),
      debug = true
    )
  }

  def testFlatSpec() {
    addFileToProject("FlatSpecTest.scala",
      """
        |import org.scalatest._
        |
        |class FlatSpecTest extends FlatSpec with GivenWhenThen {
        | "A FlatSpecTest" should "be able to run single test" in {
        |   Given("an empty test case")
        |   val resultToPrint = ">>TEST: OK<<"
        |
        |   When("the result line is printed")
        |   print(resultToPrint)
        |
        |   Then("nothing happens in the test")
        |
        |   info("and that's quite right")
        | }
        |
        | it should "not run other tests" in {
        |   print(">>TEST: FAILED<<")
        | }
        |}
      """.stripMargin.trim()
    )

    runTestByLocation(7, 1, "FlatSpecTest.scala",
      checkConfigAndSettings(_, "FlatSpecTest", "A FlatSpecTest should be able to run single test"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "FlatSpecTest", "A FlatSpecTest", "should be able to run single test") &&
      checkResultTreeDoesNotHaveNodes(root, "should not run other tests"),
      debug = true
    )
  }

  def testFreeSpec() {
    addFileToProject("FreeSpecTest.scala",
      """
        |import org.scalatest._
        |
        |class FreeSpecTest extends FreeSpec {
        |  "A FreeSpecTest" - {
        |    "should be able to run single tests" in {
        |      print(">>TEST: OK<<")
        |    }
        |
        |    "should not run tests that are not selected" in {
        |      print(">>TEST: FAILED<<")
        |    }
        |  }
        |}
      """.stripMargin
    )

    runTestByLocation(6, 3, "FreeSpecTest.scala",
      checkConfigAndSettings(_, "FreeSpecTest", "A FreeSpecTest should be able to run single tests"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "FreeSpecTest", "A FreeSpecTest", "should be able to run single tests") &&
        checkResultTreeDoesNotHaveNodes(root, "should not run tests that are not selected"),
      debug = true
    )
  }

  def testFreeSpecPath() {
    addFileToProject("FreeSpecPathTest.scala",
    """
      |import org.scalatest._
      |
      |class FreeSpecPathTest extends path.FreeSpec {
      |  "A FreeSpecTest" - {
      |    "should be able to run single test" in {
      |      print(">>TEST: OK<<")
      |    }
      |
      |    "should not run tests that are not selected" in {
      |      print("nothing interesting: path.FreeSpec executes contexts anyway")
      |    }
      |  }
      |}
    """.stripMargin)

    runTestByLocation(5, 15, "FreeSpecPathTest.scala",
      checkConfigAndSettings(_, "FreeSpecPathTest", "A FreeSpecTest should be able to run single test"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "FreeSpecPathTest", "A FreeSpecTest", "should be able to run single test") &&
          checkResultTreeDoesNotHaveNodes(root, "should not run tests that are not selected"),
      debug = true
    )
  }

  def testFunSpec() {
    addFileToProject("FunSpecTest.scala",
    """
      |import org.scalatest._
      |
      |class FunSpecTest extends FunSpec {
      |  describe("FunSpecTest") {
      |    it ("should launch single test") {
      |      print(">>TEST: OK<<")
      |    }
      |
      |    it ("should not launch other tests") {
      |      print(">>TEST: FAILED<<")
      |    }
      |  }
      |}
    """.stripMargin
    )

    runTestByLocation(6, 9, "FunSpecTest.scala",
      checkConfigAndSettings(_, "FunSpecTest", "FunSpecTest should launch single test"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "FunSpecTest", "FunSpecTest", "should launch single test") &&
        checkResultTreeDoesNotHaveNodes(root, "should not launch other tests"),
      debug = true
    )
  }

  def testFunSuite() {
    addFileToProject("FunSuiteTest.scala",
    """
      |import org.scalatest._
      |
      |class FunSuiteTest extends FunSuite {
      |
      |  test("should not run other tests") {
      |    print(">>TEST: FAILED<<")
      |  }
      |
      |  test("should run single test") {
      |    print(">>TEST: OK<<")
      |  }
      |}
    """.stripMargin
    )

    runTestByLocation(9, 8, "FunSuiteTest.scala",
      checkConfigAndSettings(_, "FunSuiteTest", "should run single test"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "FunSuiteTest", "should run single test") &&
        checkResultTreeDoesNotHaveNodes(root, "should not run other tests"),
      debug = true
    )
  }

  def testPropSpec() {
    addFileToProject("PropSpecTest.scala",
    """
      |import org.scalatest._
      |
      |class PropSpecTest extends PropSpec {
      |
      |  property("Single tests should run") {
      |    print(">>TEST: OK<<")
      |  }
      |
      |  property("other test should not run") {
      |    print(">>TEST: FAILED<<")
      |  }
      |}
    """.stripMargin
    )

    runTestByLocation(5, 5, "PropSpecTest.scala",
      checkConfigAndSettings(_, "PropSpecTest", "Single tests should run"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "PropSpecTest", "Single tests should run") &&
        checkResultTreeDoesNotHaveNodes(root, "other tests should not run"),
      debug = true
    )
  }

  def testWordSpec() {
    addFileToProject("WordSpecTest.scala",
    """
      |import org.scalatest._
      |
      |class WordSpecTest extends WordSpec {
      |  "WordSpecTest" should {
      |    "Run single test" in {
      |      print(">>TEST: OK<<")
      |    }
      |
      |    "ignore other tests" in {
      |      print(">>TEST: FAILED<<")
      |    }
      |  }
      |}
    """.stripMargin
    )

    runTestByLocation(5, 10, "WordSpecTest.scala",
      checkConfigAndSettings(_, "WordSpecTest", "WordSpecTest should Run single test"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "WordSpecTest", "WordSpecTest", "Run single test") &&
        checkResultTreeDoesNotHaveNodes(root, "ignore other tests"),
      debug = true
    )
  }
}
