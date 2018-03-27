package org.jetbrains.plugins.scala.testingSupport.scalatest.fileStructureView

import org.jetbrains.plugins.scala.lang.structureView.element.Test._
import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

/**
  * @author Roman.Shein
  * @since 21.04.2015.
  */
trait PropSpecFileStructureViewTest extends ScalaTestTestCase {
  private val className = "PropSpecViewTest"

  addSourceFile(className + ".scala",
    s"""
       |import org.scalatest._
       |
       |class $className extends PropSpec {
       |  property("test") {}
       |
       |  ignore("ignore") {}
       |
       |  property("pending") (pending)
       |
       |  ignore("pending and ignore") (pending)
       |}
      """.stripMargin)

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
