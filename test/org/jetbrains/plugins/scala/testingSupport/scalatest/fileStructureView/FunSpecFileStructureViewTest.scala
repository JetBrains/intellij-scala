package org.jetbrains.plugins.scala.testingSupport.scalatest.fileStructureView

import org.jetbrains.plugins.scala.lang.structureView.elements.impl.TestStructureViewElement._
import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 20.04.2015.
 */
trait FunSpecFileStructureViewTest extends IntegrationTest {
  private val className = "FunSpecViewTest"

  def addFunSpecViewTest(): Unit = {
    addFileToProject(className + ".scala",
      """
        |import org.scalatest._
        |
        |class FunSpecTest extends FunSpec {
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
  }

  def testFunSpecNormal() {
    addFunSpecViewTest()
    runFileStructureViewTest(className, normalStatusId, "describe(\"parent\")", "it (\"child1\")")
  }

  def testFunSpecHierarchy(): Unit = {
    addFunSpecViewTest()
    runFileStructureViewTest(className, "it (\"child1\")", Some("describe(\"parent\")"))
  }

  def testFunSpecIgnored(): Unit = {
    addFunSpecViewTest()
    runFileStructureViewTest(className, ignoredStatusId, "ignore (\"ignore1\")")
  }

  def testFunSpecIgnoredAndPending(): Unit = {
    addFunSpecViewTest()
    runFileStructureViewTest(className, ignoredStatusId, "ignore(\"pending_and_ignore\")")
  }

  def testFunSpecPending(): Unit = {
    addFunSpecViewTest()
    runFileStructureViewTest(className, pendingStatusId, "describe(\"pending\")", "they (\"child2\")")
  }
}
