package org.jetbrains.plugins.scala.testingSupport.utest.utest_0_9

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import org.jetbrains.plugins.scala.testingSupport.utest.UTestTestCase

trait UTest_0_9_CompanionsCombinationTest extends UTestTestCase {

  private val TestFileName = "myTests.scala"

  addSourceFile(TestFileName,
    //language=Scala
    """import utest._
      |
      |object MyTestObjectWithDollarInTheEnd1$ extends TestSuite {
      |  override def tests: Tests = Tests { test("test 1") {} }
      |}
      |
      |class MyTestClassWithDollarInTheEnd1$ extends TestSuite {
      |  override def tests: Tests = Tests { test("test 1") {} }
      |}
      |
      |object MyTestObject extends TestSuite {
      |  override def tests: Tests = Tests {test("test 1") {} }
      |}
      |
      |class MyTestClass extends TestSuite {
      |  override def tests: Tests = Tests {test("test 1") {} }
      |}
      |
      |object MyTestClassWithCompanion
      |class MyTestClassWithCompanion extends TestSuite {
      |  override def tests: Tests = Tests {test("test 1") {} }
      |}
      |
      |class MyTestObjectWithCompanion
      |object MyTestObjectWithCompanion extends TestSuite {
      |  override def tests: Tests = Tests {test("test 1") {} }
      |}
      |
      |class MyTestClassWithCompanionTestObject extends TestSuite {
      |  override def tests: Tests = Tests {test("test from class") {} }
      |}
      |object MyTestClassWithCompanionTestObject extends TestSuite {
      |  override def tests: Tests = Tests {test("test from object") {} }
      |}
      |""".stripMargin.trim())

  def testTestObjectWithDollarInTheEnd1(): Unit =
    runTestByLocation(loc(TestFileName, 2, 3),
      assertConfigAndSettings(_, "MyTestObjectWithDollarInTheEnd1$"),
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "MyTestObjectWithDollarInTheEnd1$", "tests", "test 1"),
        ))
        assertResultTreeStatus(root, Magnitude.COMPLETE_INDEX)
      }
    )

  def testTestClassWithDollarInTheEnd1(): Unit =
    runTestByLocation(loc(TestFileName, 6, 3),
      assertConfigAndSettings(_, "MyTestClassWithDollarInTheEnd1$"),
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "MyTestClassWithDollarInTheEnd1$", "tests", "test 1"),
        ))
        assertResultTreeStatus(root, Magnitude.COMPLETE_INDEX)
      }
    )

  def testTestObject(): Unit =
    runTestByLocation(loc(TestFileName, 10, 3),
      assertConfigAndSettings(_, "MyTestObject"),
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "MyTestObject", "tests", "test 1"),
        ))
        assertResultTreeStatus(root, Magnitude.COMPLETE_INDEX)
      }
    )

  def testTestClass(): Unit =
    runTestByLocation(loc(TestFileName, 14, 3),
      assertConfigAndSettings(_, "MyTestClass"),
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "MyTestClass", "tests", "test 1"),
        ))
        assertResultTreeStatus(root, Magnitude.COMPLETE_INDEX)
      }
    )

  def testTestClassWithCompanion(): Unit =
    runTestByLocation(loc(TestFileName, 19, 3),
      assertConfigAndSettings(_, "MyTestClassWithCompanion"),
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "MyTestClassWithCompanion", "tests", "test 1"),
        ))
        assertResultTreeStatus(root, Magnitude.COMPLETE_INDEX)
      }
    )

  def testTestObjectWithCompanion(): Unit =
    runTestByLocation(loc(TestFileName, 24, 3),
      assertConfigAndSettings(_, "MyTestObjectWithCompanion"),
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "MyTestObjectWithCompanion", "tests", "test 1"),
        ))
        assertResultTreeStatus(root, Magnitude.COMPLETE_INDEX)
      }
    )

  def testTestClassWithCompanionTestObject_FromClass(): Unit =
    runTestByLocation(loc(TestFileName, 28, 3),
      assertConfigAndSettings(_, "MyTestClassWithCompanionTestObject"),
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          //NOTE!!!
          //Yes, it's expected that it's "test from object"
          //In this strange edge case, when both class and object extend TestSuite, uTest will also only detect the object as the test
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "MyTestClassWithCompanionTestObject", "tests", "test from object"),
        ))
        assertResultTreeStatus(root, Magnitude.COMPLETE_INDEX)
      }
    )

  def testTestClassWithCompanionTestObject_FromObject(): Unit =
    runTestByLocation(loc(TestFileName, 31, 3),
      assertConfigAndSettings(_, "MyTestClassWithCompanionTestObject"),
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "MyTestClassWithCompanionTestObject", "tests", "test from object"),
        ))
        assertResultTreeStatus(root, Magnitude.COMPLETE_INDEX)
      }
    )
}
