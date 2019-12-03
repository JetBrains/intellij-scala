package org.jetbrains.plugins.scala.testingSupport.utest.scala2_13.utest_0_7_1

class UTestBeforeAfterTest_2_13_0_7_1 extends UTestTestBase_2_13_0_7_1 {

  val beforeAfterTestName = "BeforeAfterTest"
  val beforeAfterFileName = beforeAfterTestName + ".scala"

  addSourceFile(beforeAfterFileName,
    s"""
       |import utest._
       |
       |object $beforeAfterTestName extends TestSuite {
       |  val tests = Tests {
       |    test("test1") {}
       |  }
       |
       |  override def utestBeforeEach(path: Seq[String]): Unit = { println(">>TEST: BEFORE <<") }
       |
       |  override def utestAfterEach(path: Seq[String]): Unit = { println(">>TEST: AFTER <<") }
       |}
       |""".stripMargin.trim())

  def testBefore(): Unit = {


    runTestByLocation2(4, 10, beforeAfterFileName,
      assertConfigAndSettings(_, beforeAfterTestName, "tests\\test1"),
      _ => true, expectedText = "BEFORE", checkOutputs = true)
  }

  def testAfter(): Unit = {
    runTestByLocation2(4, 10, beforeAfterFileName,
      assertConfigAndSettings(_, beforeAfterTestName, "tests\\test1"),
      _ => true, expectedText = "AFTER", checkOutputs = true)
  }
}
