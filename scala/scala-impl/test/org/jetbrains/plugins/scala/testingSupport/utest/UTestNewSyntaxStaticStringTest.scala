package org.jetbrains.plugins.scala.testingSupport.utest

trait UTestNewSyntaxStaticStringTest extends UTestStaticStringBaseTest {

  addSourceFile(StaticStringTestFileName,
    s"""
       |import utest._
       |
       |object $StaticStringTestName extends TestSuite {
       |  val tests = Tests {
       |    val valName = "valName"
       |
       |    test(valName) {}
       |
       |    test("sum" + "Name") {}
       |
       |    test("nonConst" + System.currentTimeMillis()) {}
       |
       |    test("  testTrim  ".trim() + 1) {}
       |
       |    test("LOWERCASE".toLowerCase()) {}
       |
       |    test("prefixsuffix".stripSuffix("suffix")) {}
       |
       |    test("prefixsuffix".stripPrefix("prefix")) {}
       |
       |    test("junkTest1".substring(4)) {}
       |
       |    test("junkTest2junk".substring(4, 9)) {}
       |
       |    test("junkplace".replace("junk", "re")) {}
       |  }
       |}
      """.stripMargin)

  def testVal(): Unit = checkTest(7, 10, "valName")
  def testSum(): Unit = checkTest(9, 12, "sumName")
  def testNonConst(): Unit = checkTest(11, 15)
  def testTrim(): Unit = checkTest(13, 15, "testTrim1")
  def testToLowerCase(): Unit = checkTest(15, 18, "lowercase")
  def testSuffix(): Unit = checkTest(17, 15, "prefix")
  def testPrefix(): Unit = checkTest(19, 15, "suffix")
  def testSubString1(): Unit = checkTest(21, 15, "Test1")
  def testSubString2(): Unit = checkTest(23, 15, "Test2")
  def testReplace(): Unit = checkTest(25, 15, "replace")
}
