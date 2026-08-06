package org.jetbrains.plugins.scala.testingSupport.scalatest.base.fileStructureView

import org.jetbrains.plugins.scala.structureView.element.Test._
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

trait FunSuiteFileStructureViewTest extends ScalaTestTestCase {
  private val className = "FunSuiteViewTest"

  addSourceFile(className + ".scala",
    s"""$ImportsForFunSuite
       |
       |class $className extends $FunSuiteBase {
       |  test("test") {}
       |
       |  ignore("ignore") {}
       |
       |  test("pending") (pending)
       |
       |  ignore("pending and ignore") (pending)
       |}
       |""".stripMargin)

  def testFunSuiteNormal(): Unit = {
    runFileStructureViewTest(className, NormalStatusId, "test(\"test\")")
  }

  def testFunSuiteIgnored(): Unit = {
    runFileStructureViewTest(className, IgnoredStatusId, "ignore(\"ignore\")")
  }

  def testFunSuitePending(): Unit = {
    runFileStructureViewTest(className, PendingStatusId, "test(\"pending\")")
  }

  def testFunSuiteIgnoredAndPending(): Unit = {
    runFileStructureViewTest(className, IgnoredStatusId, "ignore(\"pending and ignore\")")
  }
}
