package org.jetbrains.plugins.scala.testingSupport.scalatest.generators

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

trait FunSpecGenerator extends ScalaTestTestCase {

  val funSpecClassName = "FunSpecTest"
  val funSpecFileName: String = funSpecClassName + ".scala"

  addSourceFile(funSpecFileName,
    s"""
       |import org.scalatest._
       |
       |class $funSpecClassName extends FunSpec {
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
