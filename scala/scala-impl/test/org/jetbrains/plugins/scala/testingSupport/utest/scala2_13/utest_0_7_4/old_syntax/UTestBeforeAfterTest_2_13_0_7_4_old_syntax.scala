package org.jetbrains.plugins.scala.testingSupport.utest.scala2_13.utest_0_7_4.old_syntax

import org.jetbrains.plugins.scala.testingSupport.utest.scala2_13.utest_0_7_4.UTestTestBase_2_13_0_7_4

class UTestBeforeAfterTest_2_13_0_7_4_old_syntax extends UTestTestBase_2_13_0_7_4 {

  val beforeAfterTestName = "BeforeAfterTest"
  val beforeAfterFileName = beforeAfterTestName + ".scala"

  addSourceFile(beforeAfterFileName,
    s"""
       |import utest._
       |
       |object $beforeAfterTestName extends TestSuite {
       |  val tests = Tests {
       |    "test1" - {}
       |  }
       |
       |  override def utestBeforeEach(path: Seq[String]): Unit = { println(">>TEST: BEFORE <<") }
       |
       |  override def utestAfterEach(path: Seq[String]): Unit = { println(">>TEST: AFTER <<") }
       |}
      """.stripMargin.trim())

  def testBefore(): Unit = {
    runTestByLocation2(4, 10, beforeAfterFileName,
      assertConfigAndSettings(_, beforeAfterTestName, "tests\\test1"),
      DoNotCheck, expectedText = "BEFORE", checkOutputs = true)
  }

  def testAfter(): Unit = {
    runTestByLocation2(4, 10, beforeAfterFileName,
      assertConfigAndSettings(_, beforeAfterTestName, "tests\\test1"),
      DoNotCheck, expectedText = "AFTER", checkOutputs = true)
  }
}
