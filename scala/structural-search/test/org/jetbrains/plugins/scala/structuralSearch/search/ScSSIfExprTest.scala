package org.jetbrains.plugins.scala.structuralSearch.search

import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchTestCase

class ScSSIfExprTest extends ScalaStructuralSearchTestCase {

  def testBasicIf(): Unit = {
    val contentMatch =
      """<match="AA">if (a) b
        |else c</match="AA">
        |"""
    val contentNoMatch =
      """if (a) b
        |else c
        |"""

    val patternRight =
      """if (a) b
        |else c
        |"""
    val patternCondWrong =
      """if (d) b
        |else c
        |"""
    val patternThenWrong =
      """if (a) d
        |else c
        |"""
    val patternElseWrong =
      """if (a) b
        |else d
        |"""

    matchAndAssert(
      "IF Basic - All match",
      contentMatch, patternRight,
    )
    matchAndAssert(
      "IF Basic - Cond wrong",
      contentNoMatch, patternCondWrong,
    )
    matchAndAssert(
      "IF Basic - Then wrong",
      contentNoMatch, patternThenWrong,
    )
    matchAndAssert(
      "IF Basic - Else wrong",
      contentNoMatch, patternElseWrong,
    )
  }

  def testIgnoreBrackets(): Unit = {
    val contentPure =
      """<match="AA">if (a) b
        |else c</match="AA">
        |"""
    val contentBrackets =
      """<match="AA">if (a) {
        |  b
        |} else {
        |  c
        |}</match="AA">
        |"""
    val contentMixed =
      """<match="AA">if (a) b
        |else {
        |  c
        |}</match="AA">
        |"""
    val contentFilled =
      """if (a) b
        |else {
        |  c
        |  d
        |}
        |"""
    val contentFilledMatch =
      """<match="AA">if (a) b
        |else {
        |  c
        |  d
        |}</match="AA">
        |"""

    val patternPure =
      """if (a) b
        |else c
        |"""
    val patternBrackets =
      """if (a) {
        |  b
        |} else {
        |  c
        |}
        |"""
    val patternVars =
      """if (a) $b$
        |else $c$
        |"""

    matchAndAssert(
      "IF Ignore brackets - Pure match Pure",
      contentPure, patternPure,
    )
    matchAndAssert(
      "IF Ignore brackets - Pure match Brackets",
      contentBrackets, patternPure,
    )
    matchAndAssert(
      "IF Ignore brackets - Pure match Mixed",
      contentMixed, patternPure,
    )
    matchAndAssert(
      "IF Ignore brackets - Brackets match Pure",
      contentPure, patternBrackets,
    )
    matchAndAssert(
      "IF Ignore brackets - Brackets match Brackets",
      contentBrackets, patternBrackets,
    )
    matchAndAssert(
      "IF Ignore brackets - Brackets match Mixed",
      contentMixed, patternBrackets,
    )

    matchAndAssert(
      "IF Ignore brackets - Pure does not match Filled",
      contentFilled, patternPure,
    )
    matchAndAssert(
      "IF Ignore brackets - Brackets does not match Filled",
      contentFilled, patternBrackets,
    )

    matchAndAssert(
      "IF Ignore brackets - Pattern with count matches Filled",
      contentFilledMatch, patternVars,
      matchOptions =>
        matchOptions.addNewVariableConstraint("c")
          .setMaxCount(10)
    )
  }

  def testNested(): Unit = {
    val content =
      """<match="AB">if (a) b
        |else {
        |  <match="AA">if (c) d
        |  else e</match="AA">
        |}</match="AB">
        |"""
    val pattern =
      """if ($a$) $b$
        |else $c$
        |"""

    matchAndAssert(
      "Nested ifs with vars",
      content,
      pattern
    )
  }
}
