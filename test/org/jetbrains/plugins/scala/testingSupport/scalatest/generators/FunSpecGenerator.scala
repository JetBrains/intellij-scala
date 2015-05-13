package org.jetbrains.plugins.scala.testingSupport.scalatest.generators

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 10.02.2015.
 */
trait FunSpecGenerator extends IntegrationTest {
  def funSpecClassName = "FunSpecTest"
  def funSpecFileName = funSpecClassName + ".scala"

  def addFunSpec() {
    addFileToProject("FunSpecTest.scala",
      """
        |import org.scalatest._
        |
        |class FunSpecTest extends FunSpec {
        |  describe("FunSpecTest") {
        |    it ("should launch single test") {
        |      print(">>TEST: OK<<")
        |    }
        |
        |    it ("should not launch other tests") {
        |      print(">>TEST: FAILED<<")
        |    }
        |  }
        |
        |  describe("OtherScope") {
        |    it ("is here") {}
        |  }
        |
        |  describe("emptyScope") {}
        |}
      """.stripMargin.trim()
    )
  }
}
