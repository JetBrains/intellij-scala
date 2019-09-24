package org.jetbrains.plugins.scala.testingSupport.scalatest.staticStringTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

trait FunSpecStaticStringTest extends ScalaTestTestCase {
  val funSpecClassName = "FunSpecStringTest"
  val funSpecFileName = funSpecClassName + ".scala"

  addSourceFile(funSpecFileName,
    s"""
      |import org.scalatest._
      |
      |class $funSpecClassName extends FunSpec {
      |  val constName = "consts"
      |  describe("FunSpecTest") {
      |    it (constName) {
      |    }
      |
      |    it ("works " + "with sums") {
      |    }
      |  }
      |
      |  describe("Sum of " + constName) {
      |    it ("works with " + constName) {}
      |  }
      |
      |  describe("emptyScope") {
      |    it ("does not create " + runConfig()) {}
      |  }
      |}
      |
    """.stripMargin.trim())

  def testFunSpecSum(): Unit = {
    assertConfigAndSettings(createTestFromLocation(8, 10, funSpecFileName), funSpecClassName,
      "FunSpecTest works with sums")
  }

  def testFunSpecVal(): Unit = {
    assertConfigAndSettings(createTestFromLocation(5, 10, funSpecFileName), funSpecClassName,
      "FunSpecTest consts")
  }

  def testFunSpecValSum(): Unit = {
  }
}
