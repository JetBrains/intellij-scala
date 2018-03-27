package org.jetbrains.plugins.scala.testingSupport.scalatest.fileStructureView

import org.jetbrains.plugins.scala.lang.structureView.element.Test._
import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

/**
  * @author Roman.Shein
  * @since 21.04.2015.
  */
trait WordSpecFileStructureViewTest extends ScalaTestTestCase {
  private val className = "WordSpecViewTest"

  addSourceFile(className + ".scala",
    s"""
      |import org.scalatest._
      |
      |class $className extends WordSpec {
      |  "parent1" should {
      |    "child1" in {}
      |
      |    "child2" when {}
      |
      |    "pending1" in pending
      |
      |    "pending2" is pending
      |  }
      |
      |  "parent2" which {
      |    "child3" must {}
      |    "child4" can {}
      |    "ignore1" ignore {}
      |    "ignore2" ignore pending
      |  }
      |}
    """.stripMargin)

  def testWordSpecNormal(): Unit = {
    runFileStructureViewTest(className, NormalStatusId, "\"parent1\"", "\"child1\"", "\"child2\"", "\"parent2\"",
      "\"child3\"", "\"child4\"")
  }

  def testWordSpecHierarchy(): Unit = {
    runFileStructureViewTest(className, "\"child1\"", Some("\"parent1\""))
    runFileStructureViewTest(className, "\"child2\"", Some("\"parent1\""))
    runFileStructureViewTest(className, "\"child3\"", Some("\"parent2\""))
    runFileStructureViewTest(className, "\"child4\"", Some("\"parent2\""))
  }

  def testWordSpecIgnored(): Unit = {
    runFileStructureViewTest(className, IgnoredStatusId, "\"ignore1\"")
  }

  def testWordSpecPending(): Unit = {
    runFileStructureViewTest(className, PendingStatusId, "\"pending1\"")
    runFileStructureViewTest(className, PendingStatusId, "\"pending2\"")
  }

  def testWordSpecIgnoredAndPending(): Unit = {
    runFileStructureViewTest(className, IgnoredStatusId, "\"ignore2\"")
  }
}
