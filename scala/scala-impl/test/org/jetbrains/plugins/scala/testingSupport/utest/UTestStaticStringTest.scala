package org.jetbrains.plugins.scala.testingSupport.utest

/**
  * @author Roman.Shein
  * @since 04.09.2015.
  */
trait UTestStaticStringTest extends UTestTestCase {
  private val StaticStringTestName = "UTestStaticStringTest"
  private val StaticStringTestFileName = s"$StaticStringTestName.scala"

  addSourceFile(StaticStringTestFileName,
    s"""
       |import utest._
       |$testSuiteSecondPrefix
       |
       |object $StaticStringTestName extends TestSuite {
       |  val tests = TestSuite {
       |    val valName = "valName"
       |
       |    valName - {}
       |
       |    "sum" + "Name" - {}
       |
       |    "nonConst" + System.currentTimeMillis() - {}
       |
       |    "  testTrim  ".trim() + 1 - {}
       |
       |    "LOWERCASE".toLowerCase() - {}
       |
       |    "prefixsuffix".stripSuffix("suffix") - {}
       |
       |    "prefixsuffix".stripPrefix("prefix") - {}
       |
       |    "junkTest1".substring(4) - {}
       |
       |    "junkTest2junk".substring(4, 9) - {}
       |
       |    "junkplace".replace("junk", "re") - {}
       |  }
       |}
      """.stripMargin)

  protected def checkTest(lineNumber: Int, position: Int, expectedName: String): Unit = {
    val testName = "tests" + (if (expectedName.isEmpty) "" else "\\" + expectedName)
    checkTest(lineNumber, position, Seq(testName))
  }

  protected def checkTest(lineNumber: Int, position: Int, expectedNames: Seq[String] = Nil): Unit = {
    val configuration = createTestFromLocation(lineNumber, position, StaticStringTestFileName)
    assertConfigAndSettings(configuration, StaticStringTestName, expectedNames: _*)
  }

  def testSum(): Unit = {
    checkTest(9, 7, "sumName")
  }

  def testVal(): Unit = {
    checkTest(7, 5, "valName")
  }

  def testNonConst(): Unit = {
    checkTest(11, 10)
  }

  def testTrim(): Unit = {
    checkTest(13, 10, "testTrim1")
  }

  def testToLowerCase(): Unit = {
    checkTest(15, 13, "lowercase")
  }

  def testSuffix(): Unit = {
    checkTest(17, 10, "prefix")
  }

  def testPrefix(): Unit = {
    checkTest(19, 10, "suffix")
  }

  def testSubString1(): Unit = {
    checkTest(21, 10, "Test1")
  }

  def testSubString2(): Unit = {
    checkTest(23, 10, "Test2")
  }

  def testReplace(): Unit = {
    checkTest(25, 10, "replace")
  }
}
