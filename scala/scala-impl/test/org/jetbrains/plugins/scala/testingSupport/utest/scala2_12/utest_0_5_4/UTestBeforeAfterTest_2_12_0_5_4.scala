package org.jetbrains.plugins.scala.testingSupport.utest.scala2_12.utest_0_5_4

import org.jetbrains.plugins.scala.SlowTests
import org.junit.experimental.categories.Category

class UTestBeforeAfterTest_2_12_0_5_4 extends UTestTestBase_2_12_0_5_4 {

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
    runTestByLocation(4, 10, beforeAfterFileName,
      checkConfigAndSettings(_, beforeAfterTestName, "tests\\test1"),
      _ => true, expectedText = "BEFORE", checkOutputs = true)
  }

  def testAfter(): Unit = {
    runTestByLocation(4, 10, beforeAfterFileName,
      checkConfigAndSettings(_, beforeAfterTestName, "tests\\test1"),
      _ => true, expectedText = "AFTER", checkOutputs = true)
  }
}
