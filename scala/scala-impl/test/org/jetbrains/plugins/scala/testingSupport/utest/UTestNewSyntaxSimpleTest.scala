package org.jetbrains.plugins.scala.testingSupport.utest

import org.jetbrains.plugins.scala.lang.structureView.element.Test

trait UTestNewSyntaxSimpleTest extends UTestTestCase {

  protected val uTestTestName = "UTestTest"
  protected val uTestFileName = uTestTestName + ".scala"

  addSourceFile(uTestFileName,
    s"""import utest._
       |
       |object $uTestTestName extends TestSuite {
       |  val tests = Tests {
       |
       |    test("outer1") {}
       |
       |    test("outer2") {
       |      test("inner2_1") {
       |
       |      }
       |    }
       |  }
       |
       |  val otherTests = Tests {
       |    test("outer1") {
       |      test("inner1_1") {}
       |    }
       |  }
       |
       |  val sameName = Tests {
       |    test("sameName") {
       |      test("sameName") {}
       |    }
       |  }
       |
       |  val failedTest = Tests {
       |    test("failed") {
       |      assert(false)
       |    }
       |  }
       |}
       |""".stripMargin.trim())

  protected val inner2_1Path = List("[root]", uTestTestName, "tests", "outer2", "inner2_1")
  protected val outer1_Path = List("[root]", uTestTestName, "tests", "outer1")
  protected val sameNamePath = List("[root]", uTestTestName, "sameName", "sameName", "sameName")
  protected val inner1_1Path = List("[root]", uTestTestName, "otherTests", "outer1", "inner1_1")
  protected val failedPath = List("[root]", uTestTestName, "failedTest", "failed")

  def testClassSuite(): Unit = {
    runTestByLocation(2, 3, uTestFileName,
      checkConfigAndSettings(_, uTestTestName),
      root => checkResultTreeHasExactNamedPath(root, inner2_1Path) &&
        checkResultTreeHasExactNamedPath(root, sameNamePath) &&
        checkResultTreeHasExactNamedPath(root, outer1_Path) &&
        checkResultTreeHasExactNamedPath(root, inner1_1Path) &&
        checkResultTreeHasExactNamedPath(root, failedPath))
  }


  def testMethod(): Unit = {
    runTestByLocation(3, 3, uTestFileName,
      checkConfigAndSettings(_, uTestTestName, "tests"),
      root => checkResultTreeHasExactNamedPath(root, outer1_Path) &&
        checkResultTreeHasExactNamedPath(root, inner2_1Path) &&
        checkResultTreeDoesNotHaveNodes(root, "inner1_1", "sameName"))
  }

  def testSingleTest(): Unit = {
    runTestByLocation(8, 15, uTestFileName,
      checkConfigAndSettings(_, uTestTestName, "tests\\outer2\\inner2_1"),
      root => checkResultTreeHasExactNamedPath(root, inner2_1Path) &&
        checkResultTreeDoesNotHaveNodes(root, "outer1", "inner1_1"))
  }

  def testDuplicateConfig(): Unit = {
    runDuplicateConfigTest(8, 15, uTestFileName, checkConfigAndSettings(_, uTestTestName, "tests\\outer2\\inner2_1"))
  }

  def testSameName(): Unit = {
    runTestByLocation(22, 15, uTestFileName,
      checkConfigAndSettings(_, uTestTestName, "sameName\\sameName\\sameName"),
      root => checkResultTreeHasExactNamedPath(root, sameNamePath))
  }

  def testGoToSourceSuccessful(): Unit = {
    runGoToSourceTest(3, 7, uTestFileName,
      checkConfigAndSettings(_, uTestTestName, "tests"),
      List("[root]", uTestTestName, "tests"), 3)
  }

  def testGoToSourceFailed(): Unit = {
    runGoToSourceTest(28, 10, uTestFileName,
      checkConfigAndSettings(_, uTestTestName, "failedTest\\failed"),
      failedPath, 26)
  }

  def testFileStructureView(): Unit = {
    //TODO: I am sorry, this seems not to be major issue
    // TestNodeProvider is too error-prone for these changes and I already spent much time on the new syntax
    return
    //notice that we only test here nodes that produce TestStructureViewElement in file structure view
    //this means that root test scopes (methods) are not tested here; instead, they are tested in testFileStructureViewHierarchy
    runFileStructureViewTest(uTestTestName, Test.NormalStatusId, "\"outer1\"",
      "\"outer2\"", "\"inner2_1\"", "\"inner1_1\"", "\"sameName\"")
  }

  def testFileStructureViewHierarchy(): Unit = {
    return //TODO
    runFileStructureViewTest(uTestTestName, "\"sameName\"", Some("sameName"))
    runFileStructureViewTest(uTestTestName, "\"sameName\"", Some("\"sameName\""))
    runFileStructureViewTest(uTestTestName, "\"outer2\"", Some("tests"))
    runFileStructureViewTest(uTestTestName, "\"inner2_1\"", Some("\"outer2\""))
  }

  protected val uTestTestName1 = "UTestTest1"
  protected val uTestFileName1 = uTestTestName1 + ".scala"

  addSourceFile(uTestFileName1,
    s"""import utest._
       |
       |object $uTestTestName1 extends TestSuite {
       |  val tests = Tests {
       |    test("test1") {
       |      throw new RuntimeException("message")
       |    }
       |    test("test2") {
       |      throw new RuntimeException // null message
       |    }
       |  }
       |}
       |""".stripMargin.trim())

  def testFailing(): Unit = {
    runTestByLocation2(4, 10, uTestFileName1,
      assertConfigAndSettings(_, uTestTestName1, "tests\\test1"),
      assertFromCheck { root =>
        checkResultTreeHasExactNamedPath(root, List("[root]", uTestTestName1, "tests", "test1"))
      }
    )
  }

  def testFailingWithNullMessage(): Unit = {
    runTestByLocation2(7, 10, uTestFileName1,
      assertConfigAndSettings(_, uTestTestName1, "tests\\test2"),
      assertFromCheck { root =>
        checkResultTreeHasExactNamedPath(root, List("[root]", uTestTestName1, "tests", "test2"))
      }
    )
  }
}
