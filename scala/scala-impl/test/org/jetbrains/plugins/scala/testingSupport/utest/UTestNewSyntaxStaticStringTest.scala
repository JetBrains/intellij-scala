package org.jetbrains.plugins.scala.testingSupport.utest

trait UTestNewSyntaxStaticStringTest extends UTestStaticStringBaseTest {

  addSourceFile(StaticStringTestFileName,
    s"""import utest._
       |
       |object $StaticStringTestName extends TestSuite {
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
