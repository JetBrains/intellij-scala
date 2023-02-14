package org.jetbrains.plugins.scala.testingSupport.utest.scala2_13

import org.jetbrains.plugins.scala.testingSupport.utest.UTestNewSyntaxStaticStringTest

class UTestStaticStringTest_2_13 extends UTestTestBase_2_13 with UTestNewSyntaxStaticStringTest {

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
