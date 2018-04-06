package org.jetbrains.plugins.scala.testingSupport.utest

import org.jetbrains.plugins.scala.lang.structureView.element.Test

/**
  * @author Roman.Shein
  * @since 13.05.2015.
  */
trait UTestSimpleTest extends UTestTestCase {

  protected val uTestTestName = "UTestTest"

  protected val uTestFileName = uTestTestName + ".scala"

  addSourceFile(uTestFileName,
    s"""
       |import utest._
       |$testSuiteSecondPrefix
       |
       |object UTestTest extends TestSuite {
       |  val tests = TestSuite {
       |    "outer1" - {}
       |
       |    "outer2" - {
       |      "inner2_1" - {}
       |    }
       |  }
       |
       |  val otherTests = TestSuite {
       |    "outer1" - {
       |      "inner1_1" - {}
       |    }
       |  }
       |
       |  val sameName = TestSuite {
       |    "sameName" - {
       |      "sameName" - {}
       |    }
       |  }
       |
       |  val failedTest = TestSuite {
       |    "failed" - {
       |      assert(false)
       |    }
       |  }
       |}
      """.stripMargin.trim())

  protected val inner2_1Path = List("[root]", uTestTestName, "tests", "outer2", "inner2_1")
  protected val outer1_Path = List("[root]", uTestTestName, "tests", "outer1")
  protected val sameNamePath = List("[root]", uTestTestName, "sameName", "sameName", "sameName")
  protected val inner1_1Path = List("[root]", uTestTestName, "otherTests", "outer1", "inner1_1")
  protected val failedPath = List("[root]", uTestTestName, "failedTest", "failed")

  def testSingleTest(): Unit = {
    runTestByLocation(8, 10, uTestFileName,
      checkConfigAndSettings(_, uTestTestName, "tests\\outer2\\inner2_1"),
      root => checkResultTreeHasExactNamedPath(root, inner2_1Path) &&
        checkResultTreeDoesNotHaveNodes(root, "outer1", "inner1_1"))
  }

  def testSameName(): Unit = {
    runTestByLocation(20, 10, uTestFileName,
      checkConfigAndSettings(_, uTestTestName, "sameName\\sameName\\sameName"),
      root => checkResultTreeHasExactNamedPath(root, sameNamePath))
  }

  //TODO for some reason, tests are launched, but never reported (also, jvm running the test hangs and never terminates, even after root idea process is terminated)
  def testMethod(): Unit = {
    runTestByLocation(4, 3, uTestFileName,
      checkConfigAndSettings(_, uTestTestName, "tests"),
      root => checkResultTreeHasExactNamedPath(root, outer1_Path) &&
        checkResultTreeHasExactNamedPath(root, inner2_1Path) &&
        checkResultTreeDoesNotHaveNodes(root, "inner1_1", "sameName"))
  }

  def testClassSuite(): Unit = {
    runTestByLocation(3, 3, uTestFileName,
      checkConfigAndSettings(_, uTestTestName),
      root => checkResultTreeHasExactNamedPath(root, inner2_1Path) &&
        checkResultTreeHasExactNamedPath(root, sameNamePath) &&
        checkResultTreeHasExactNamedPath(root, outer1_Path) &&
        checkResultTreeHasExactNamedPath(root, inner1_1Path) &&
        checkResultTreeHasExactNamedPath(root, failedPath))
  }

  def testFileStructureView(): Unit = {
    //notice that we only test here nodes that produce TestStructureViewElement in file structure view
    //this means that root test scopes (methods) are not tested here; instead, they are tested in testFileStructureViewHierarchy
    runFileStructureViewTest(uTestTestName, Test.NormalStatusId, "\"outer1\"",
      "\"outer2\"", "\"inner2_1\"", "\"inner1_1\"", "\"sameName\"")
  }

  def testFileStructureViewHierarchy(): Unit = {
    runFileStructureViewTest(uTestTestName, "\"sameName\"", Some("sameName"))
    runFileStructureViewTest(uTestTestName, "\"sameName\"", Some("\"sameName\""))
    runFileStructureViewTest(uTestTestName, "\"outer2\"", Some("tests"))
    runFileStructureViewTest(uTestTestName, "\"inner2_1\"", Some("\"outer2\""))
  }

  def testDuplicateConfig(): Unit = {
    runDuplicateConfigTest(8, 10, uTestFileName, checkConfigAndSettings(_, uTestTestName, "tests\\outer2\\inner2_1"))
  }

  def testGoToSourceSuccessful(): Unit = {
    runGoToSourceTest(4, 7, uTestFileName,
      checkConfigAndSettings(_, uTestTestName, "tests"),
      List("[root]", uTestTestName, "tests"), 4)
  }


  def testGoToSourceFailed(): Unit = {
    //notice that 'goToSource' now travels only to method: right now, we don't identify exact line of code in test
    //execution completion callback
    runGoToSourceTest(26, 5, uTestFileName,
      checkConfigAndSettings(_, uTestTestName, "failedTest\\failed"),
      failedPath, 24)
  }
}
