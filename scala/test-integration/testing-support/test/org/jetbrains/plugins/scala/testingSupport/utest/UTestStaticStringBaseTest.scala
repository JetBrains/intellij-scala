package org.jetbrains.plugins.scala.testingSupport.utest

trait UTestStaticStringBaseTest extends UTestTestCase {

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
}
