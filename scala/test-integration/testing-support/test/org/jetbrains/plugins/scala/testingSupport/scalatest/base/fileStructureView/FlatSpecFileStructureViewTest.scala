package org.jetbrains.plugins.scala.testingSupport.scalatest.base.fileStructureView

import org.jetbrains.plugins.scala.structureView.element.Test._
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

trait FlatSpecFileStructureViewTest extends ScalaTestTestCase {

  private val className = "FlatSpecViewTest"

  addSourceFile(className + ".scala",
    s"""$ImportsForFlatSpec
       |
       |class $className extends $FlatSpecBase {
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
       |""".stripMargin)

  def testFlatSpecNormal(): Unit =
    runFileStructureViewTest(className, NormalStatusId, "it should \"child1\"", "they should \"child2\"")

  def testFlatSpecIgnored(): Unit =
    runFileStructureViewTest(className, IgnoredStatusId, "ignore should \"ignore1\"", "they should \"ignore2\"")

  def testFlatSpecIgnoredAndPending(): Unit =
    runFileStructureViewTest(className, IgnoredStatusId, "ignore should \"ignore and pend\"", "it should \"ignore and pend2\"")

  def testFlatSpecPending(): Unit =
    runFileStructureViewTest(className, PendingStatusId, "\"second\" should \"pend1\"", "it should \"pend2\"")
}
