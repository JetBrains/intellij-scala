package org.jetbrains.plugins.scala.structuralSearch.search

import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchTestCase

class ScSSDoExprTest extends ScalaStructuralSearchTestCase {

  def testBasic(): Unit = {
    val content =
      """<match="AA">do b while (a)</match="AA">
        |"""
    matchAndAssert(
      "IF Basic",
      content, "do b while (a)",
    )
  }

  def testIgnoreBrackets(): Unit = {
    val content =
      """<match="AA">do b while (a)</match="AA">
        |<match="AB">do {
        | b
        |} while (a)</match="AB">
        |<match="AC">do {
        | b
        | c
        |} while (a)</match="AC">
        |"""

    matchAndAssert(
      "IF Ignore brackets - Pure",
      clearMarker(content, Set("AA", "AB")), "do b while (a)",
    )
    matchAndAssert(
      "IF Ignore brackets - Brackets",
      clearMarker(content, Set("AA", "AB")),
      """do {
        | b
        |} while (a)
        |"""
    )
    matchAndAssert(
      "IF Ignore brackets - Var",
      clearMarker(content, Set("AA", "AB")), "do $b$ while (a)",
    )
    matchAndAssert(
      "IF Ignore brackets - Var with count",
      content,
      """do {
        | $b$
        |} while (a)
        |""",
      _.addNewVariableConstraint("b").setMaxCount(10)
    )
  }
}
