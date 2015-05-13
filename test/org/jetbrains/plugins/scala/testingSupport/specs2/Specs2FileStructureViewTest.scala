package org.jetbrains.plugins.scala.testingSupport.specs2

import org.jetbrains.plugins.scala.lang.structureView.elements.impl.TestStructureViewElement._

/**
 * @author Roman.Shein
 * @since 20.04.2015.
 */
abstract class Specs2FileStructureViewTest extends Specs2TestCase {

  private def prepareAndRunTestInner(status: Int, tests: String*) = {
    prepareFile()
    runFileStructureViewTest("SpecsFileStrctureViewTest", status, tests:_*)
  }

  protected def prepareFile(): Unit = {
    addFileToProject("SpecsFileStrctureViewTest.scala",
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
  }

  def testShouldView(): Unit = prepareAndRunTestInner(normalStatusId, "\"parent\"")

  def testExclamationView(): Unit = prepareAndRunTestInner(normalStatusId, "\"child1\"")

  def testGreaterView(): Unit = prepareAndRunTestInner(normalStatusId, "\"child2\"")

  def testInView(): Unit = prepareAndRunTestInner(normalStatusId, "\"child3\"")

  def testCanView(): Unit = prepareAndRunTestInner(normalStatusId, "\"parent2\"")

  def testPending(): Unit = prepareAndRunTestInner(pendingStatusId, "\"pending\"", "\"pending2\"")

  def testHierarchy(): Unit = {
    prepareFile()
    runFileStructureViewTest("SpecsFileStrctureViewTest", "\"child1\"", Some("\"parent\""))
  }
}
