package org.jetbrains.plugins.scala.testingSupport.utest.scala2_12.old_syntax

import org.jetbrains.plugins.scala.testingSupport.utest.UTestStaticStringTest
import org.jetbrains.plugins.scala.testingSupport.utest.scala2_12.UTestTestBase_2_12

class UTestStaticStringTest_2_12_old_syntax extends UTestTestBase_2_12 with UTestStaticStringTest {

  protected val testsTestName = "UTestTests"

  protected val testsTestFileName: String = testsTestName + ".scala"

  addSourceFile(testsTestFileName,
    s"""
       |import utest._
       |$testSuiteSecondPrefix
       |
       |object $testsTestName extends TestSuite {
       |  val tests = Tests {
       |    "foo" - {}
       |  }
       |}
      """.stripMargin)

  def testLeft(): Unit = {
    checkTestsTest(5, 7, "")
  }

  def testRight(): Unit = {
    checkTestsTest(5, 18, "")
  }

  def testInner(): Unit = {
    checkTestsTest(6, 6, "foo")
  }

  protected def checkTestsTest(lineNumber: Int, position: Int, expectedName: String): Unit = {
    assertConfigAndSettings(createTestCaretLocation(lineNumber, position, testsTestFileName),
      testsTestName, "tests" + (if (expectedName.isEmpty) "" else "\\" + expectedName))
  }
}
