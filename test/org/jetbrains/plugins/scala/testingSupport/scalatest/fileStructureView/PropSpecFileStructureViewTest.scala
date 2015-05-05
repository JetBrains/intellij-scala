package org.jetbrains.plugins.scala.testingSupport.scalatest.fileStructureView

import org.jetbrains.plugins.scala.lang.structureView.elements.impl.TestStructureViewElement._
import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 21.04.2015.
 */
trait PropSpecFileStructureViewTest extends IntegrationTest {
  private val className = "PropSpecViewTest"

  def addPropSpecViewTest(): Unit = {
    addFileToProject(className + ".scala",
      """
        |import org.scalatest._
        |
        |class PropSpecViewTest extends PropSpec {
        |  property("test") {}
        |
        |  ignore("ignore") {}
        |
        |  property("pending") (pending)
        |
        |  ignore("pending and ignore") (pending)
        |}
      """.stripMargin)
  }

  def testPropSpecNormal(): Unit = {
    addPropSpecViewTest()
    runFileStructureViewTest(className, normalStatusId, "property(\"test\")")
  }

  def testPropSpecIgnored(): Unit = {
    addPropSpecViewTest()
    runFileStructureViewTest(className, ignoredStatusId, "ignore(\"ignore\")")
  }

  def testPropSpecPending(): Unit = {
    addPropSpecViewTest()
    runFileStructureViewTest(className, pendingStatusId, "property(\"pending\")")
  }

  def testPropSpecIgnoredAndPending(): Unit = {
    addPropSpecViewTest()
    runFileStructureViewTest(className, ignoredStatusId, "ignore(\"pending and ignore\")")
  }

}
