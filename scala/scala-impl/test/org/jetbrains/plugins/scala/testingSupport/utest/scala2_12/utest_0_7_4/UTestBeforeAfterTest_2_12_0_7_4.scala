package org.jetbrains.plugins.scala.testingSupport.utest.scala2_12.utest_0_7_4

class UTestBeforeAfterTest_2_12_0_7_4 extends UTestTestBase_2_12_0_7_4 {

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

  def testBefore(): Unit =
    runTestByLocation2(4, 10, beforeAfterFileName,
      assertConfigAndSettings(_, beforeAfterTestName, "tests\\test1"),
      DoNotCheck, expectedText = "BEFORE", checkOutputs = true)

  def testAfter(): Unit =
    runTestByLocation2(4, 10, beforeAfterFileName,
      assertConfigAndSettings(_, beforeAfterTestName, "tests\\test1"),
      DoNotCheck, expectedText = "AFTER", checkOutputs = true)
}
