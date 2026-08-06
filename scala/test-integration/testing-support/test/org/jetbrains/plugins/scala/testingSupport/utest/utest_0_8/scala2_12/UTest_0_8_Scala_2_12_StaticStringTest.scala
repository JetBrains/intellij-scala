package org.jetbrains.plugins.scala.testingSupport.utest.utest_0_8.scala2_12

import org.jetbrains.plugins.scala.testingSupport.utest.utest_0_8.UTest_0_8_StaticStringTest

class UTest_0_8_Scala_2_12_StaticStringTest extends UTest_0_8_Scala_2_12_TestBase with UTest_0_8_StaticStringTest {

  protected val testsTestName = "UTestTests"

  protected val testsTestFileName: String = testsTestName + ".scala"

  addSourceFile(testsTestFileName,
    s"""
       |import utest._
       |
       |object $testsTestName extends TestSuite {
       |  val tests = Tests {
       |    test("foo") {}
       |  }
       |}
      """.stripMargin)

  def testLeft(): Unit = checkTestsTest(4, 7, "")
  def testRight(): Unit = checkTestsTest(4, 18, "")
  def testInner(): Unit = checkTestsTest(5, 5, "foo")
  def testInner_1(): Unit = checkTestsTest(5, 11, "foo")
  def testInner_2(): Unit = checkTestsTest(5, 17, "foo")

  protected def checkTestsTest(lineNumber: Int, position: Int, expectedName: String): Unit =
    assertConfigAndSettings(
      createTestCaretLocation(lineNumber, position, testsTestFileName),
      testsTestName,
      s"tests${if (expectedName.isEmpty) "" else "\\" + expectedName}"
    )
}
