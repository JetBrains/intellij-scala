package org.jetbrains.plugins.scala.testingSupport.scalatest.generators

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

/**
  * @author Roman.Shein
  * @since 10.02.2015.
  */
trait FunSuiteGenerator extends ScalaTestTestCase {
  def addFunSuite() {
    addFileToProject("FunSuiteTest.scala",
      """
        |import org.scalatest._
        |
        |class FunSuiteTest extends FunSuite {
        |
        |  test("should not run other tests") {
        |    print(">>TEST: FAILED<<")
        |  }
        |
        |  test("should run single test") {
        |    print(">>TEST: OK<<")
        |  }
        |}
      """.stripMargin.trim()
    )
  }
}
