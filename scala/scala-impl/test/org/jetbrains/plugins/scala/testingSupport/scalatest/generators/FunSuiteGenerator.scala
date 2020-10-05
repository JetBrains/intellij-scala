package org.jetbrains.plugins.scala.testingSupport.scalatest.generators

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

trait FunSuiteGenerator extends ScalaTestTestCase {

  val funSuiteClassName = "FunSuiteTest"
  val funSuiteFileName = funSuiteClassName + ".scala"

  addSourceFile(funSuiteFileName,
    s"""
      |import org.scalatest._
      |
      |class $funSuiteClassName extends FunSuite {
      |
      |  test("should not run other tests") {
      |    print("$TestOutputPrefix FAILED $TestOutputSuffix")
      |  }
      |
      |  test("should run single test") {
      |    print("$TestOutputPrefix OK $TestOutputSuffix")
      |  }
      |
      |  test("tagged", FunSuiteTag) {}
      |}
      |
      |object FunSuiteTag extends Tag("MyTag")
    """.stripMargin.trim()
  )
}
