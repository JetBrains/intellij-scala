package org.jetbrains.plugins.scala.structuralSearch.search

import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchTestCase

class ScSSWhileExprTest extends ScalaStructuralSearchTestCase {

  def testBasic(): Unit = {
    val content =
      """<match="AA">while (a) b</match="AA">
        |"""
    matchAndAssert(
      "IF Basic",
      content, "while (a) b",
    )
  }

  def testIgnoreBrackets(): Unit = {
    val content =
      """<match="AA">while (a) b</match="AA">
        |<match="AB">while (a) {
        | b
        |}</match="AB">
        |<match="AC">while (a) {
        | b
        | c
        |}</match="AC">
        |"""

    matchAndAssert(
      "IF Ignore brackets - Pure",
      clearMarker(content, Set("AA", "AB")), "while (a) b",
    )
    matchAndAssert(
      "IF Ignore brackets - Brackets",
      clearMarker(content, Set("AA", "AB")),
      """while (a) {
        | b
        |}
        |"""
    )
    matchAndAssert(
      "IF Ignore brackets - Var",
      clearMarker(content, Set("AA", "AB")), "while (a) $b$",
    )
    matchAndAssert(
      "IF Ignore brackets - Var with count",
      content,
      """while (a) {
        | $b$
        |}
        |""",
      _.addNewVariableConstraint("b").setMaxCount(10)
    )
  }
}
