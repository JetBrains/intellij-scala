package org.jetbrains.plugins.scala.testingSupport.scalatest.fileStructureView

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest
import org.jetbrains.plugins.scala.lang.structureView.elements.impl.TestStructureViewElement._

/**
 * @author Roman.Shein
 * @since 19.04.2015.
 */
trait FlatSpecFileStructureViewTest extends IntegrationTest {
  private val className = "FlatSpecViewTest"


  def addFlatSpecViewTest() {
    addFileToProject(className + ".scala",
    """
      |import org.scalatest._
      |
      |class FlatSpecViewTest extends FlatSpec {
      |  behavior of "first"
      |
      |  it should "child1" in {}
      |
      |  ignore should "ignore1" in {}
      |
      |  "second" should "pend1" in pending
      |
      |  it should "pend2" is pending
      |
      |  they should "child2" in {}
      |
      |  they should "ignore2" ignore {}
      |
      |  ignore should "ignore and pend" is pending
      |
      |  it should "ignore and pend2" ignore pending
      |}
    """.stripMargin)
  }

  def testFlatSpecNormal(): Unit = {
    addFlatSpecViewTest()
    runFileStructureViewTest(className, normalStatusId, "it should \"child1\"", "they should \"child2\"")
  }

  def testFlatSpecIgnored(): Unit = {
    addFlatSpecViewTest()
    runFileStructureViewTest(className, ignoredStatusId, "ignore should \"ignore1\"",
      "they should \"ignore2\"")
  }

  def testFlatSpecIgnoredAndPending(): Unit = {
    addFlatSpecViewTest()
    runFileStructureViewTest(className, ignoredStatusId, "ignore should \"ignore and pend\"", "it should \"ignore and pend2\"")
  }

  def testFlatSpecPending(): Unit = {
    addFlatSpecViewTest()
    runFileStructureViewTest(className, pendingStatusId, "\"second\" should \"pend1\"", "it should \"pend2\"")
  }
}
