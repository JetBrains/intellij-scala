package org.jetbrains.plugins.scala.testingSupport.scalatest.base.staticStringTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

trait FunSpecStaticStringTest extends ScalaTestTestCase {

  private val ClassName = "FunSpecStringTest"
  private val FileName = ClassName + ".scala"

  addSourceFile(FileName,
    s"""$ImportsForFunSpec
       |
       |class $ClassName extends $FunSpecBase {
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
       |    it ("does not create " + foo()) {}
       |  }
       |
       |  def foo(): String = "foo"
       |}
       |""".stripMargin)

  def testFunSpecSum(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(8, 10, FileName), ClassName,
      "FunSpecTest works with sums")
  }

  def testFunSpecVal(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(5, 10, FileName), ClassName,
      "FunSpecTest consts")
  }

  def testFunSpecValSum(): Unit = {
  }
}
