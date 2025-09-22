package org.jetbrains.plugins.scala.structuralSearch.search

import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchTestCase

class ScSSIfExprTest extends ScalaStructuralSearchTestCase {

  def testBasicIf(): Unit = {
    val contentMatch =
      """<match="AA">if (a) b
        |else c</match="AA">
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
      clearMarker(contentMatch), patternCondWrong,
    )
    matchAndAssert(
      "IF Basic - Then wrong",
      clearMarker(contentMatch), patternThenWrong,
    )
    matchAndAssert(
      "IF Basic - Else wrong",
      clearMarker(contentMatch), patternElseWrong,
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
      _.addNewVariableConstraint("c").setMaxCount(10)
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
      content, pattern
    )
  }

  def testCombinationWithCorrectVariable(): Unit = {
    val content =
      """object B {
        |  def test(a: Int, b: Int, abara: Int): Int = {
        |    if (a == 5) {
        |      a + b + abara
        |    } else {
        |      a.+(b) + abara
        |    }
        |  }
        |
        |  def test2(a: Int, b: Int, abara: Int): Int = {
        |    <match="AA">if (a == 5) {
        |      a + b
        |    } else {
        |      a.+(b) + abara
        |    }</match="AA">
        |  }
        |}
        |"""
    val pattern1 =
      """if ($a$) $b$
        |else $b$ + abara
        |"""
    val pattern2 =
      """if ($a$) $b$ + abara
        |else $b$
        |"""

    matchAndAssert(
      "Combination to test correct variable behaviour",
      content, pattern1
    )
    matchAndAssert(
      "Combination to test correct variable behaviour (anti)",
      clearMarker(content), pattern2
    )
  }

  def testCombinationWithCorrectVariable2(): Unit = {
    val content =
      """object B {
        |  def test(a: Int, b: Int, abara: Int): Int = {
        |    <match="AA">if (a == 5) {
        |      a + b + abara
        |    } else {
        |      a.+(b) + abara
        |    }</match="AA">
        |  }
        |
        |  def test2(a: Int, b: Int, abara: Int): Int = {
        |    <match="AB">if (a == 5) {
        |      a + b + abara
        |    } else {
        |      a + b + abara
        |    }</match="AB">
        |  }
        |}
        |"""
    val pattern1 =
      """if ($a$) $b$
        |else $b$
        |"""
    val pattern2 =
      """if ($a$) $b$
        |else $b$ + abara
        |"""

    matchAndAssert(
      "Combination to test correct variable behaviour 2",
      content, pattern1
    )
    matchAndAssert(
      "Combination to test correct variable behaviour 2 (anti)",
      clearMarker(content), pattern2
    )
  }
}
