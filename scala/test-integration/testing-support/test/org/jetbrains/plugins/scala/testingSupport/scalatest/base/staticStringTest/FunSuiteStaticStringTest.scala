package org.jetbrains.plugins.scala.testingSupport.scalatest.base.staticStringTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

trait FunSuiteStaticStringTest extends ScalaTestTestCase {

  private val ClassName = "FunSuiteStringTest"
  private val FileName = ClassName + ".scala"

  addSourceFile(FileName,
    s"""$ImportsForFunSuite
       |
       |class $ClassName extends $FunSuiteBase {
       |
       |  val constName = "consts"
       |  test("should" + " work with sums") {
       |  }
       |
       |  test(constName) {
       |  }
       |
       |  test("should sum " + constName) {
       |  }
       |}
       |""".stripMargin
  )

  def testFunSuiteSum(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(5, 10, FileName), ClassName,
      "should work with sums")
  }

  def testFunSuiteVal(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(8, 10, FileName), ClassName,
      "consts")
  }

  def testFunSuiteValSum(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(11, 10, FileName), ClassName,
      "should sum consts")
  }

}
