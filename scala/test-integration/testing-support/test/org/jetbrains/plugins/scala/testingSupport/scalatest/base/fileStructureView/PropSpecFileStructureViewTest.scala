package org.jetbrains.plugins.scala.testingSupport.scalatest.base.fileStructureView

import org.jetbrains.plugins.scala.structureView.element.Test._
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

trait PropSpecFileStructureViewTest extends ScalaTestTestCase {

  private val className = "PropSpecViewTest"

  addSourceFile(className + ".scala",
    s"""$ImportsForPropSpec
       |
       |class $className extends $PropSpecBase {
       |  property("test") {}
       |
       |  ignore("ignore") {}
       |
       |  property("pending") (pending)
       |
       |  ignore("pending and ignore") (pending)
       |}
       |""".stripMargin)

  def testPropSpecNormal(): Unit = {
    runFileStructureViewTest(className, NormalStatusId, "property(\"test\")")
  }

  def testPropSpecIgnored(): Unit = {
    runFileStructureViewTest(className, IgnoredStatusId, "ignore(\"ignore\")")
  }

  def testPropSpecPending(): Unit = {
    runFileStructureViewTest(className, PendingStatusId, "property(\"pending\")")
  }

  def testPropSpecIgnoredAndPending(): Unit = {
    runFileStructureViewTest(className, IgnoredStatusId, "ignore(\"pending and ignore\")")
  }

}
