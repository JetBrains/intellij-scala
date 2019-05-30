package org.jetbrains.plugins.scala.testingSupport.scalatest.staticStringTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

/**
  * @author Roman.Shein
  * @since 26.06.2015.
  */
trait FunSuiteStaticStringTest extends ScalaTestTestCase {
  val funSuiteClassName = "FunSuiteStringTest"
  val funSuiteFileName = funSuiteClassName + ".scala"

  addSourceFile(funSuiteFileName,
    s"""
      |import org.scalatest._
      |
      |class $funSuiteClassName extends FunSuite {
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
    """.stripMargin.trim()
  )

  def testFunSuiteSum() = {
    assertConfigAndSettings(createTestFromLocation(5, 10, funSuiteFileName), funSuiteClassName,
      "should work with sums")
  }

  def testFunSuiteVal() = {
    assertConfigAndSettings(createTestFromLocation(8, 10, funSuiteFileName), funSuiteClassName,
      "consts")
  }

  def testFunSuiteValSum() = {
    assertConfigAndSettings(createTestFromLocation(11, 10, funSuiteFileName), funSuiteClassName,
      "should sum consts")
  }

}
