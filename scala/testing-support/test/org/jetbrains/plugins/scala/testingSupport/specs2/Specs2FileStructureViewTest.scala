package org.jetbrains.plugins.scala.testingSupport.specs2

import org.jetbrains.plugins.scala.lang.structureView.element.Test._

abstract class Specs2FileStructureViewTest extends Specs2TestCase {

  private def prepareAndRunTestInner(status: Int, tests: String*): Unit = {
    runFileStructureViewTest("SpecsFileStrctureViewTest", status, tests:_*)
  }

  addSourceFile("SpecsFileStrctureViewTest.scala",
    """|import org.specs2.mutable.Specification
      |
      |class SpecsFileStrctureViewTest extends Specification {
      |
      |  "parent" should {
      |    "child1" ! {
      |      success
      |    }
      |    "child2" >> {
      |      success
      |    }
      |
      |    "child3" in {
      |      success
      |    }
      |
      |    "pending" in {
      |      success
      |    }.pendingUntilFixed
      |
      |    "pending2" in {
      |      success
      |    }.pendingUntilFixed("message")
      |  }
      |
      |  "parent2" can {
      |    "child" in {
      |      success
      |    }
      |  }
      |}
    """.stripMargin
  )

  def testShouldView(): Unit = prepareAndRunTestInner(NormalStatusId, "\"parent\"")

  def testExclamationView(): Unit = prepareAndRunTestInner(NormalStatusId, "\"child1\"")

  def testGreaterView(): Unit = prepareAndRunTestInner(NormalStatusId, "\"child2\"")

  def testInView(): Unit = prepareAndRunTestInner(NormalStatusId, "\"child3\"")

  def testCanView(): Unit = prepareAndRunTestInner(NormalStatusId, "\"parent2\"")

  def testPending(): Unit = prepareAndRunTestInner(PendingStatusId, "\"pending\"", "\"pending2\"")

  def testHierarchy(): Unit = {
    runFileStructureViewTest("SpecsFileStrctureViewTest", "\"child1\"", Some("\"parent\""))
  }
}
