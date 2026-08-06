package org.jetbrains.plugins.scala.structuralSearch.search

import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchTestCase

class ScSSExprTest extends ScalaStructuralSearchTestCase {

  def testInfixExpr1(): Unit = {
    val content =
      """(<match="AA">a + b</match="AA">) / (<match="AB">c + b</match="AB">) * (x * z)
        |"""
    val pattern =
      """$a$ + b
        |"""
    matchAndAssert(
      "InfixExpr 1",
      content, pattern
    )
  }

  def testInfixExpr2(): Unit = {
    val content =
      """<match="AA">(a + b) / (c + b)</match="AA"> * (x * z)
        |"""
    val pattern =
      """(a + $b$) / (c + $b$)
        |"""
    matchAndAssert(
      "InfixExpr 2",
      content, pattern
    )
  }

  def testInfixExpr3(): Unit = {
    val content =
      """<match="AA">(a + b) / (c + b) * (x * z)</match="AA">
        |"""
    val pattern =
      """$expr$ * (x * $z$)
        |"""
    matchAndAssert(
      "InfixExpr 3",
      content, pattern
    )
  }

  def testFunctionCalls(): Unit = {
    val content =
      """<match="AA">printf("%3$02d:%2$02d:%1$02d", 30, 45, 9)</match="AA">
        |<match="AB">printf("%3$02d:%2$02d:%1$02d", 30, 45, 9, 21, 1, 2002)</match="AB">
        |"""
    val pattern1 =
      """printf($a$, $b$, $c$, $d$)
        |"""
    val pattern2 =
      """printf($a$)
        |"""

    matchAndAssert(
      "FunctionCalls Basic",
      clearMarker(content, Set("AA")), pattern1
    )
    matchAndAssert(
      "FunctionCalls Parameter by count 1",
      clearMarker(content, Set("AA")), pattern2,
      _.addNewVariableConstraint("a").setMaxCount(5)
    )
    matchAndAssert(
      "FunctionCalls Parameter by count 2",
      content, pattern2,
      _.addNewVariableConstraint("a").setMaxCount(1000)
    )
  }

  def testInfixFunctionInteroperability(): Unit = {
    val functionCall =
      """<match="AA">a.+(b)</match="AA">
        |"""
    val infixExpr =
      """<match="AA">a + b</match="AA">
        |"""

    matchAndAssert(
      "FunctionCalls -> InfixExpr interoperability",
      clearMarker(infixExpr), functionCall
    )
    matchAndAssert(
      "FunctionCalls <- InfixExpr interoperability",
      clearMarker(functionCall), infixExpr
    )
  }

  def testReferences(): Unit = {
    val content =
      """<match="AA">A.B.C</match="AA">
        |<match="AB">A.D.C</match="AB">
        |<match="AC">J.B.E</match="AC">
        |<match="AD">J.F.E</match="AD">
        |<match="AE"><match="AG">A.C</match="AG">.E</match="AE">
        |<match="AF">I.D.C</match="AF">
        |"""

    matchAndAssert(
      "References 1",
      clearMarker(content, Set("AA")), "A.B.C"
    )
    matchAndAssert(
      "References 2",
      clearMarker(content, Set("AA", "AB")), "A.$b$.C"
    )
    matchAndAssert(
      "References 3",
      clearMarker(content, Set("AA", "AB", "AE")), "A.$b$.$c$"
    )
    matchAndAssert(
      "References 4",
      clearMarker(content, Set("AA", "AC")), "$a$.B.$c$"
    )
    matchAndAssert(
      "References 5",
      clearMarker(content, Set("AA", "AB", "AF")), "$a$.$b$.C"
    )
    matchAndAssert(
      "References 6",
      clearMarker(content, Set("AA", "AB", "AF", "AG")), "$a$.C"
    )
    matchAndAssert(
      "References 7",
      clearMarker(content, Set()), "B.C"
    )
  }
}
