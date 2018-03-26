package org.jetbrains.plugins.scala.testingSupport.scalatest.fileStructureView

import org.jetbrains.plugins.scala.lang.structureView.elements.impl.TestStructureViewElement._
import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

/**
  * @author Roman.Shein
  * @since 21.04.2015.
  */
trait FunSuiteFileStructureViewTest extends ScalaTestTestCase {
  private val className = "FunSuiteViewTest"

  addSourceFile(className + ".scala",
    s"""
      |import org.scalatest._
      |
      |class $className extends FunSuite {
      |  test("test") {}
      |
      |  ignore("ignore") {}
      |
      |  test("pending") (pending)
      |
      |  ignore("pending and ignore") (pending)
      |}
    """.stripMargin)

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
