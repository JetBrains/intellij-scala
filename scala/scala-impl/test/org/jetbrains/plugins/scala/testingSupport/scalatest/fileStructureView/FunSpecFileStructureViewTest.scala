package org.jetbrains.plugins.scala.testingSupport.scalatest.fileStructureView

import org.jetbrains.plugins.scala.lang.structureView.element.Test._
import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

trait FunSpecFileStructureViewTest extends ScalaTestTestCase {
  private val className = "FunSpecViewTest"

  addSourceFile(className + ".scala",
    s"""
      |import org.scalatest._
      |
      |class $className extends FunSpec {
      |  describe("parent") {
      |    it ("child1") {}
      |
      |    ignore ("ignore1") {}
      |
      |    they ("child2") (pending)
      |  }
      |
      |  describe("pending") (pending)
      |  ignore("pending_and_ignore") (pending)
      |}
    """.stripMargin)

  def testFunSpecNormal(): Unit = {
    runFileStructureViewTest(className, NormalStatusId, "describe(\"parent\")", "it (\"child1\")")
  }

  def testFunSpecHierarchy(): Unit = {
    runFileStructureViewTest(className, "it (\"child1\")", Some("describe(\"parent\")"))
  }

  def testFunSpecIgnored(): Unit = {
    runFileStructureViewTest(className, IgnoredStatusId, "ignore (\"ignore1\")")
  }

  def testFunSpecIgnoredAndPending(): Unit = {
    runFileStructureViewTest(className, IgnoredStatusId, "ignore(\"pending_and_ignore\")")
  }

  def testFunSpecPending(): Unit = {
    runFileStructureViewTest(className, PendingStatusId, "describe(\"pending\")", "they (\"child2\")")
  }
}
