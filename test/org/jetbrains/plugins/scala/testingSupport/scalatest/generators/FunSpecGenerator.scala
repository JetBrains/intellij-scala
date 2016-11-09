package org.jetbrains.plugins.scala.testingSupport.scalatest.generators

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

/**
  * @author Roman.Shein
  * @since 10.02.2015.
  */
trait FunSpecGenerator extends ScalaTestTestCase {
  val funSpecClassName = "FunSpecTest"

  val funSpecFileName = funSpecClassName + ".scala"

  addSourceFile("FunSpecTest.scala",
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
      |
      |  describe("taggedScope") {
      |    it ("is tagged", FunSpecTag) {}
      |  }
      |}
      |
      |object FunSpecTag extends Tag("MyTag")
    """.stripMargin.trim()
  )
}
