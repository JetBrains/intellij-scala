package org.jetbrains.plugins.scala.testingSupport.utest.utest_0_9

import org.jetbrains.plugins.scala.testingSupport.utest.UTestTestCase

trait UTest_0_9_BeforeAfterTest extends UTestTestCase {

  val beforeAfterTestName = "BeforeAfterTest"
  val beforeAfterFileName = beforeAfterTestName + ".scala"

  addSourceFile(beforeAfterFileName,
    s"""
       |import utest._
       |
       |class $beforeAfterTestName extends TestSuite {
       |  val tests = Tests {
       |    test("test1") {}
       |  }
       |
       |  override def utestBeforeEach(path: Seq[String]): Unit = { println("$TestOutputPrefix BEFORE $TestOutputSuffix") }
       |
       |  override def utestAfterEach(path: Seq[String]): Unit = { println("$TestOutputPrefix AFTER $TestOutputSuffix") }
       |}
       |""".stripMargin.trim())

  def testBefore(): Unit =
    runTestByLocation(
      loc(beforeAfterFileName, 4, 10),
      assertConfigAndSettings(_, beforeAfterTestName, "tests\\test1"),
      IgnoreTreeResult,
      output => assertTestOutputTextContains("BEFORE", output)
    )

  def testAfter(): Unit =
    runTestByLocation(
      loc(beforeAfterFileName, 4, 10),
      assertConfigAndSettings(_, beforeAfterTestName, "tests\\test1"),
      IgnoreTreeResult,
      output => assertTestOutputTextContains("AFTER", output)
    )
}
