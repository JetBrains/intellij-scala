package org.jetbrains.plugins.scala.testingSupport.utest

trait UTestStaticStringTest extends UTestStaticStringBaseTest {

  addSourceFile(StaticStringTestFileName,
    s"""import utest._
       |$testSuiteSecondPrefix
       |
       |object $StaticStringTestName extends TestSuite {
       |  val tests = TestSuite {
       |    "name" - {}
       |
       |    "sum" + "Name" - {}
       |  }
       |}
      """.stripMargin)

  def testVal(): Unit = checkTest(5, 5, "name")
  def testSum(): Unit = checkTest(7, 7, "sumName")
}
