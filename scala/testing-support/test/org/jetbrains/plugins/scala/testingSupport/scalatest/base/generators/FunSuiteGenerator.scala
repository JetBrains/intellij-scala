package org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

trait FunSuiteGenerator extends ScalaTestTestCase {

  protected val funSuiteClassName = "FunSuiteTest"
  protected val funSuiteFileName = funSuiteClassName + ".scala"

  addSourceFile(funSuiteFileName,
    s"""$ImportsForFunSuite
       |
       |class $funSuiteClassName extends $FunSuiteBase {
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
       |""".stripMargin
  )
}
