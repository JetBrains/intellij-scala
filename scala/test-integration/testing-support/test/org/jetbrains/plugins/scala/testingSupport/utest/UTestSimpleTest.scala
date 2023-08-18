package org.jetbrains.plugins.scala.testingSupport.utest

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import org.jetbrains.plugins.scala.structureView.element.Test

trait UTestSimpleTest extends UTestTestCase {

  private val ClassName = "UTestTest"
  private val FileName = ClassName + ".scala"

  addSourceFile(FileName,
    s"""
       |import utest._
       |$testSuiteSecondPrefix
       |
       |object $ClassName extends TestSuite {
       |  val tests = TestSuite {
       |    "outer1" - {}
       |
       |    "outer2" - {
       |      "inner2_1" - {}
       |    }
       |
       |    "sameName" - {
       |      "sameName" - {}
       |    }
       |
       |    "failed" - {
       |      assert(false)
       |    }
       |  }
       |}
      """.stripMargin.trim())

  protected val outer1_Path  = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", ClassName, "tests", "outer1")
  protected val inner2_1Path = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", ClassName, "tests", "outer2", "inner2_1")
  protected val sameNamePath = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", ClassName, "tests", "sameName", "sameName")
  protected val failedPath   = TestNodePathWithStatus(Magnitude.FAILED_INDEX, "[root]", ClassName, "tests", "failed")

  def testSingleTest(): Unit =
    runTestByLocation(
      loc(FileName, 8, 10),
      assertConfigAndSettings(_, ClassName, "tests\\outer2\\inner2_1"),
      root => {
        assertResultTreeHasSinglePath(root, inner2_1Path)
      }
    )

  def testSameName(): Unit =
    runTestByLocation(
      loc(FileName, 12, 10),
      assertConfigAndSettings(_, ClassName, "tests\\sameName\\sameName"),
      root => assertResultTreeHasSinglePath(root, sameNamePath)
    )

  def testClassSuite(): Unit =
    runTestByLocation(
      loc(FileName, 3, 3),
      assertConfigAndSettings(_, ClassName),
      root => assertResultTreePathsEqualsUnordered(root)(Seq(
        outer1_Path,
        inner2_1Path,
        sameNamePath,
        failedPath,
      ))
    )

  def testFileStructureView(): Unit = {
    //notice that we only test here nodes that produce TestStructureViewElement in file structure view
    //this means that root test scopes (methods) are not tested here; instead, they are tested in testFileStructureViewHierarchy
    runFileStructureViewTest(ClassName, Test.NormalStatusId,
      "\"outer1\"", "\"outer2\"",  "\"sameName\"", "\"failed\"")
  }

  def testFileStructureViewHierarchy(): Unit = {
    runFileStructureViewTest(ClassName, "\"outer1\"", Some("tests"))
    runFileStructureViewTest(ClassName, "\"outer2\"", Some("tests"))
    runFileStructureViewTest(ClassName, "\"inner2_1\"", Some("\"outer2\""))
    runFileStructureViewTest(ClassName, "\"sameName\"", Some("tests"))
    runFileStructureViewTest(ClassName, "\"sameName\"", Some("\"sameName\""))
    runFileStructureViewTest(ClassName, "\"failed\"", Some("tests"))
  }

  def testDuplicateConfig(): Unit =
    runDuplicateConfigTest(8, 10, FileName, assertConfigAndSettings(_, ClassName, "tests\\outer2\\inner2_1"))

  def testGoToSourceSuccessful(): Unit =
    runGoToSourceTest(
      loc(FileName, 4, 7),
      assertConfigAndSettings(_, ClassName, "tests"),
      TestNodePath("[root]", ClassName, "tests"),
      sourceLine = 4
    )

  //notice that 'goToSource' now travels only to method: right now, we don't identify exact line of code in test
  //execution completion callback
  def testGoToSourceFailed(): Unit =
    runGoToSourceTest(
      loc(FileName, 16, 5),
      assertConfigAndSettings(_, ClassName, "tests\\failed"),
      failedPath.path,
      sourceLine = 4
    )
}
