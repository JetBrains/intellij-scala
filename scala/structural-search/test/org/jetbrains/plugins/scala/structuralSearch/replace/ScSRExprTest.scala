package org.jetbrains.plugins.scala.structuralSearch.replace

import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralReplaceTestCase

class ScSRExprTest extends ScalaStructuralReplaceTestCase {

  def testSimpleExprReplacement(): Unit = {
    replaceAndAssert(
      "Simple replace",
      "a + b + c",
      "a + b",
      "ab",
      "ab + c"
    )
  }

  def testMethodCallWithVar(): Unit = {
    replaceAndAssert(
      "Replace with variable",
      """if (a == 2) {
        |  println("Hello world!")
        |}
        |""",
      "println($a$)",
      "print($a$)",
      """if (a == 2) {
        |  print("Hello world!")
        |}
        |"""
    )
  }
}
