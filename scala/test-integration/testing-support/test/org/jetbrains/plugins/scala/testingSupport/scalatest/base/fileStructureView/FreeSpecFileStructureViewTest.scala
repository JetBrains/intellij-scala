package org.jetbrains.plugins.scala.testingSupport.scalatest.base.fileStructureView

import org.jetbrains.plugins.scala.structureView.element.Test._
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider

trait FreeSpecFileStructureViewTest extends ScalaTestTestCase {

  private val className = "FreeSpecViewTest"

  addSourceFile(className + ".scala",
    s"""$ImportsForFreeSpec
       |
       |class $className extends $FreeSpecBase {
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
       |""".stripMargin)

  def testFreeSpecNormal(): Unit =
    runFileStructureViewTest(className, NormalStatusId, "\"level1\"", "\"level1_1\"", "\"level1_2\"", "\"level1_2_1\"")

  def testFreeSpecHierarchy(): Unit = {
    runFileStructureViewTest(className, "\"level1_1\"", Some("\"level1\""))
    runFileStructureViewTest(className, "\"level1_2_1\"", Some("\"level1_2\""))
  }

  def testFreeSpecIgnoredHierarchy(): Unit = {
    runFileStructureViewTest(className, "\"level2_1\"", Some("\"level2\"" + TestNodeProvider.IgnoredSuffix))
    runFileStructureViewTest(className, "\"level2_2\"" + TestNodeProvider.IgnoredSuffix, Some("\"level2\"" + TestNodeProvider.IgnoredSuffix), IgnoredStatusId)
  }

  def testFreeSpecIgnored(): Unit =
    runFileStructureViewTest(className, IgnoredStatusId, "\"level2\"", "\"level2_2\"")

  def testFreeSpecIgnoredAndPending(): Unit =
    runFileStructureViewTest(className, IgnoredStatusId, "\"level3\"")

  def testFreeSpecPending(): Unit =
    runFileStructureViewTest(className, PendingStatusId, "\"level1_2\"", "\"level1_3\"")
}
