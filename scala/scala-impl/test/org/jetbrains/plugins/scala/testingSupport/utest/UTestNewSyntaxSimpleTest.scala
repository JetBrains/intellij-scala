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
       |
       |    test("sameName") {
       |      test("sameName") {}
       |    }
       |
       |    test("failed") {
       |      assert(false)
       |    }
       |  }
       |}
       |""".stripMargin.trim())

  protected val inner2_1Path = List("[root]", uTestTestName, "tests", "outer2", "inner2_1")
  protected val outer1_Path = List("[root]", uTestTestName, "tests", "outer1")
  protected val sameNamePath = List("[root]", uTestTestName, "tests", "sameName", "sameName")
  protected val inner1_1Path = List("[root]", uTestTestName, "otherTests", "outer1", "inner1_1")
  protected val failedPath = List("[root]", uTestTestName, "tests", "failed")

  def testClassSuite(): Unit =
    runTestByLocation2(2, 3, uTestFileName,
      assertConfigAndSettings(_, uTestTestName),
      root => assertResultTreeHasExactNamedPaths(root)(Seq(
        inner2_1Path,
        sameNamePath,
        outer1_Path,
        inner1_1Path,
        failedPath,
      ))
    )

  def testSingleTest(): Unit = {
    runTestByLocation2(8, 15, uTestFileName,
      assertConfigAndSettings(_, uTestTestName, "tests\\outer2\\inner2_1"),
      root => {
        assertResultTreeHasExactNamedPaths(root)(Seq(inner2_1Path))
        assertResultTreeDoesNotHaveNodes(root, "outer1", "inner1_1")
      })
  }

  def testDuplicateConfig(): Unit = {
    runDuplicateConfigTest(8, 15, uTestFileName, assertConfigAndSettings(_, uTestTestName, "tests\\outer2\\inner2_1"))
  }

  def testSameName(): Unit = {
    runTestByLocation2(14, 15, uTestFileName,
      assertConfigAndSettings(_, uTestTestName, "tests\\sameName\\sameName"),
      root => assertResultTreeHasExactNamedPath(root, sameNamePath))
  }

  def testGoToSourceSuccessful(): Unit = {
    runGoToSourceTest(3, 7, uTestFileName,
      assertConfigAndSettings(_, uTestTestName, "tests"),
      List("[root]", uTestTestName, "tests"), 3)
  }

  def testGoToSourceFailed(): Unit = {
    runGoToSourceTest(18, 10, uTestFileName,
      assertConfigAndSettings(_, uTestTestName, "tests\\failed"),
      failedPath, 3)
  }

  def testFileStructureView(): Unit = {
    // FIXME
    return
    //notice that we only test here nodes that produce TestStructureViewElement in file structure view
    //this means that root test scopes (methods) are not tested here; instead, they are tested in testFileStructureViewHierarchy
    runFileStructureViewTest(uTestTestName, Test.NormalStatusId,
      "\"outer1\"", "\"outer2\"",  "\"sameName\"", "\"failed\"")
  }

  def testFileStructureViewHierarchy(): Unit = {
    // FIXME
    return
    runFileStructureViewTest(uTestTestName, "\"outer1\"", Some("tests"))
    runFileStructureViewTest(uTestTestName, "\"outer2\"", Some("tests"))
    runFileStructureViewTest(uTestTestName, "\"inner2_1\"", Some("\"outer2\""))
    runFileStructureViewTest(uTestTestName, "\"sameName\"", Some("tests"))
    runFileStructureViewTest(uTestTestName, "\"sameName\"", Some("\"sameName\""))
    runFileStructureViewTest(uTestTestName, "\"failed\"", Some("tests"))
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

  def testFailing(): Unit =
    runTestByLocation2(4, 10, uTestFileName1,
      assertConfigAndSettings(_, uTestTestName1, "tests\\test1"),
      root => assertResultTreeHasExactNamedPath(root, List("[root]", uTestTestName1, "tests", "test1"))
    )

  def testFailingWithNullMessage(): Unit =
    runTestByLocation2(7, 10, uTestFileName1,
      assertConfigAndSettings(_, uTestTestName1, "tests\\test2"),
      root => assertResultTreeHasExactNamedPath(root, List("[root]", uTestTestName1, "tests", "test2"))
    )
}
