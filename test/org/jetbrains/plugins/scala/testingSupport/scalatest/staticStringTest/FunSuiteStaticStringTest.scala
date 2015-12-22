package org.jetbrains.plugins.scala.testingSupport.scalatest.staticStringTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

/**
  * @author Roman.Shein
  * @since 26.06.2015.
  */
trait FunSuiteStaticStringTest extends ScalaTestTestCase {
  val funSuiteClassName = "FunSuiteStringTest"
  val funSuiteFileName = funSuiteClassName + ".scala"

  def addFunSuite(): Unit = {
    addFileToProject(funSuiteFileName,
      """
        |import org.scalatest._
        |
        |class FunSuiteStringTest extends FunSuite {
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
  }

  def testFunSuiteSum() = {
    addFunSuite()

    assert(checkConfigAndSettings(createTestFromLocation(5, 10, funSuiteFileName), funSuiteClassName,
      "should work with sums"))
  }

  def testFunSuiteVal() = {
    addFunSuite()

    assert(checkConfigAndSettings(createTestFromLocation(8, 10, funSuiteFileName), funSuiteClassName,
      "consts"))
  }

  def testFunSuiteValSum() = {
    addFunSuite()

    assert(checkConfigAndSettings(createTestFromLocation(11, 10, funSuiteFileName), funSuiteClassName,
      "should sum consts"))
  }

}
