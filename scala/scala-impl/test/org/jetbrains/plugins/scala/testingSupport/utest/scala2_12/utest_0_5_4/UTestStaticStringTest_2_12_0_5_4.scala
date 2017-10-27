package org.jetbrains.plugins.scala.testingSupport.utest.scala2_12.utest_0_5_4

import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.testingSupport.utest.UTestStaticStringTest
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  * @since 04.09.2015.
  */
@Category(Array(classOf[SlowTests]))
class UTestStaticStringTest_2_12_0_5_4 extends UTestTestBase_2_12_0_5_4 with UTestStaticStringTest {

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
    assert(checkConfigAndSettings(createTestFromLocation(lineNumber, position, testsTestFileName),
      testsTestName, "tests" + (if (expectedName.isEmpty) "" else "\\" + expectedName)))
  }
}
