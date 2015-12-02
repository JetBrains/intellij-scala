package org.jetbrains.plugins.scala.testingSupport.utest

/**
  * @author Roman.Shein
  * @since 04.09.2015.
  */
trait UTestStaticStringTest extends UTestTestCase {

  protected def staticStringTestName = "UTestStaticStringTest"

  protected def staticStringTestFileName = staticStringTestName + ".scala"

  protected def checkTest(lineNumber: Int, position: Int, expectedName: String) = {
    assert(checkConfigAndSettings(createTestFromLocation(lineNumber, position, staticStringTestFileName),
      staticStringTestName, "tests" + (if (expectedName.isEmpty) "" else "\\" + expectedName)))
  }

  protected def addTest(): Unit = {
    addFileToProject(staticStringTestFileName,
      """
        |import utest._
        |import utest.framework.TestSuite
        |
        |object UTestStaticStringTest extends TestSuite {
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
  }

  def testSum(): Unit = {
    addTest()
    checkTest(9, 7, "sumName")
  }

  def testVal(): Unit = {
    addTest()
    checkTest(7, 5, "valName")
  }

  def testNonConst(): Unit = {
    addTest()
    assert(checkConfigAndSettings(createTestFromLocation(11, 10, staticStringTestFileName),
      staticStringTestName))
  }

  def testTrim() = {
    addTest()
    checkTest(13, 10, "testTrim1")
  }

  def testToLowerCase() = {
    addTest()
    checkTest(15, 13, "lowercase")
  }

  def testSuffix() = {
    addTest()
    checkTest(17, 10, "prefix")
  }

  def testPrefix() = {
    addTest()
    checkTest(19, 10, "suffix")
  }

  def testSubString1() = {
    addTest()
    checkTest(21, 10, "Test1")
  }

  def testSubString2() = {
    addTest()
    checkTest(23, 10, "Test2")
  }

  def testReplace(): Unit = {
    addTest()
    checkTest(25, 10, "replace")
  }
}
