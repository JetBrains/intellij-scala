package org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

trait FunSpecGenerator extends ScalaTestTestCase {

  protected val funSpecClassName = "FunSpecTest"
  protected val funSpecFileName: String = funSpecClassName + ".scala"

  addSourceFile(funSpecFileName,
    s"""$ImportsForFunSpec
       |
       |class $funSpecClassName extends $FunSpecBase {
       |  describe("FunSpecTest") {
       |    it ("should launch single test") {
       |      print("$TestOutputPrefix OK $TestOutputSuffix")
       |    }
       |
       |    it ("should not launch other tests") {
       |      print("$TestOutputPrefix FAILED $TestOutputSuffix")
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
       |""".stripMargin.trim()
  )
}
