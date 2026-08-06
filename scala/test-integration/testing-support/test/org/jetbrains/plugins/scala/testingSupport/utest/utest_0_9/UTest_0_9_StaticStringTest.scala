package org.jetbrains.plugins.scala.testingSupport.utest.utest_0_9

import org.jetbrains.plugins.scala.testingSupport.utest.UTestTestCase

trait UTest_0_9_StaticStringTest extends UTestTestCase {

  protected val StaticStringTestName = "UTestStaticStringTest"
  protected val StaticStringTestFileName = s"$StaticStringTestName.scala"

  protected def checkTest(lineNumber: Int, position: Int, expectedName: String): Unit = {
    val testName = "tests" + (if (expectedName.isEmpty) "" else "\\" + expectedName)
    checkTest(lineNumber, position, Seq(testName))
  }

  protected def checkTest(lineNumber: Int, position: Int, expectedNames: Seq[String] = Nil): Unit = {
    val configuration = createTestCaretLocation(lineNumber, position, StaticStringTestFileName)
    assertConfigAndSettings(configuration, StaticStringTestName, expectedNames: _*)
  }

  addSourceFile(StaticStringTestFileName,
    s"""import utest._
       |
       |class $StaticStringTestName extends TestSuite {
       |  val tests = Tests {
       |    test("name") {}
       |
       |    test("sum" + "Name") {}
       |  }
       |}
      """.stripMargin)

  def testVal(): Unit = checkTest(4, 10, "name")
  def testSum(): Unit = checkTest(6, 12, "sumName")
}
