package org.jetbrains.plugins.scala.testingSupport.scalatest.fileStructureView

import org.jetbrains.plugins.scala.lang.structureView.elements.impl.TestStructureViewElement._
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
    runFileStructureViewTest(className, normalStatusId, "property(\"test\")")
  }

  def testPropSpecIgnored(): Unit = {
    runFileStructureViewTest(className, ignoredStatusId, "ignore(\"ignore\")")
  }

  def testPropSpecPending(): Unit = {
    runFileStructureViewTest(className, pendingStatusId, "property(\"pending\")")
  }

  def testPropSpecIgnoredAndPending(): Unit = {
    runFileStructureViewTest(className, ignoredStatusId, "ignore(\"pending and ignore\")")
  }

}
