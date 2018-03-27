package org.jetbrains.plugins.scala.testingSupport.scalatest.fileStructureView

import org.jetbrains.plugins.scala.lang.structureView.element.TestStructureViewElement._
import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider

/**
  * @author Roman.Shein
  * @since 20.04.2015.
  */
trait FreeSpecFileStructureViewTest extends ScalaTestTestCase {
  private val className = "FreeSpecViewTest"

  addSourceFile(className + ".scala",
    s"""
      |import org.scalatest._
      |
      |class $className extends FreeSpec {
      |  "level1" - {
      |    "level1_1" in {}
      |
      |    "level1_2" - {
      |      "level1_2_1" in {}
      |    }
      |
      |    "level1_2" is pending
      |
      |    "level1_3" in pending
      |  }
      |
      |  "level2" ignore {
      |    "level2_1" in {}
      |
      |    "level2_2" ignore {}
      |  }
      |
      |  "level3" ignore pending
      |}
    """.stripMargin)

  def testFreeSpecNormal(): Unit = {
    runFileStructureViewTest(className, NormalStatusId, "\"level1\"", "\"level1_1\"",
      "\"level1_2\"", "\"level1_2_1\"")
  }

  def testFreeSpecHierarchy(): Unit = {
    runFileStructureViewTest(className, "\"level1_1\"", Some("\"level1\""))
    runFileStructureViewTest(className, "\"level1_2_1\"", Some("\"level1_2\""))
  }

  def testFreeSpecIgnoredHierarchy(): Unit = {
    runFileStructureViewTest(className, "\"level2_1\"", Some("\"level2\"" + TestNodeProvider.ignoredSuffix))
    runFileStructureViewTest(className, "\"level2_2\"" + TestNodeProvider.ignoredSuffix, Some("\"level2\"" + TestNodeProvider.ignoredSuffix), IgnoredStatusId)
  }

  def testFreeSpecIgnored(): Unit = {
    runFileStructureViewTest(className, IgnoredStatusId, "\"level2\"", "\"level2_2\"")
  }

  def testFreeSpecIgnoredAndPending(): Unit = {
    runFileStructureViewTest(className, IgnoredStatusId, "\"level3\"")
  }

  def testFreeSpecPending(): Unit = {
    runFileStructureViewTest(className, PendingStatusId, "\"level1_2\"", "\"level1_3\"")
  }
}
