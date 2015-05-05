package org.jetbrains.plugins.scala.testingSupport.scalatest.fileStructureView

import org.jetbrains.plugins.scala.lang.structureView.elements.impl.TestStructureViewElement._
import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 21.04.2015.
 */
trait FunSuiteFileStructureViewTest extends IntegrationTest {
  private val className = "FunSuiteViewTest"

  def addFunSuiteViewTest(): Unit = {
    addFileToProject(className + ".scala",
      """
        |import org.scalatest._
        |
        |class FunSuiteViewTest extends FunSuite {
        |  test("test") {}
        |
        |  ignore("ignore") {}
        |
        |  test("pending") (pending)
        |
        |  ignore("pending and ignore") (pending)
        |}
      """.stripMargin)
  }

  def testFunSuiteNormal(): Unit = {
    addFunSuiteViewTest()
    runFileStructureViewTest(className, normalStatusId, "test(\"test\")")
  }

  def testFunSuiteIgnored(): Unit = {
    addFunSuiteViewTest()
    runFileStructureViewTest(className, ignoredStatusId, "ignore(\"ignore\")")
  }

  def testFunSuitePending(): Unit = {
    addFunSuiteViewTest()
    runFileStructureViewTest(className, pendingStatusId, "test(\"pending\")")
  }

  def testFunSuiteIgnoredAndPending(): Unit = {
    addFunSuiteViewTest()
    runFileStructureViewTest(className, ignoredStatusId, "ignore(\"pending and ignore\")")
  }
}
