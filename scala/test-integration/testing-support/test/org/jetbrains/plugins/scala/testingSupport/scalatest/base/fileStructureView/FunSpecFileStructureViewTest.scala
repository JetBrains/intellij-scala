package org.jetbrains.plugins.scala.testingSupport.scalatest.base.fileStructureView

import org.jetbrains.plugins.scala.structureView.element.Test._
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

trait FunSpecFileStructureViewTest extends ScalaTestTestCase {

  private val className = "FunSpecViewTest"

  addSourceFile(className + ".scala",
    s"""$ImportsForFunSpec
       |
       |class $className extends $FunSpecBase {
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
       |""".stripMargin)

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
