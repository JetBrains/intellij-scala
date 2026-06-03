package org.jetbrains.plugins.scala.structuralSearch.search

import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchTestCase

class ScSSForExprTest extends ScalaStructuralSearchTestCase {

  def testBasic(): Unit = {
    val content =
      """<match="AA">for { i <- 1 until x; if i % 2 == 0; i2 = i } println(i2)</match="AA">
        |"""
    matchAndAssert(
      "For Basic",
      content, "for { i <- 1 until x; if i % 2 == 0; i2 = i } println(i2)",
    )
  }

  def testIgnoreBrackets(): Unit = {
    val content =
      """<match="AA">for { i <- 1 until x; if i % 2 == 0; i2 = i } a</match="AA">
        |<match="AB">for { i <- 1 until x; if i % 2 == 0; i2 = i } {
        | a
        |}</match="AB">
        |<match="AC">for { i <- 1 until x; if i % 2 == 0; i2 = i } {
        | b
        | a
        |}</match="AC">
        |"""

    matchAndAssert(
      "IF Ignore brackets - Pure",
      clearMarker(content, Set("AA", "AB")), "for { i <- 1 until x; if i % 2 == 0; i2 = i } a",
    )
    matchAndAssert(
      "IF Ignore brackets - Brackets",
      clearMarker(content, Set("AA", "AB")),
      """for { i <- 1 until x; if i % 2 == 0; i2 = i } {
        |  a
        |}
        |"""
    )
    matchAndAssert(
      "IF Ignore brackets - Var",
      clearMarker(content, Set("AA", "AB")), "for { i <- 1 until x; if i % 2 == 0; i2 = i } $b$",
    )
    matchAndAssert(
      "IF Ignore brackets - Var with count",
      content,
      """for { i <- 1 until x; if i % 2 == 0; i2 = i } {
        | $b$
        |}
        |""",
      _.addNewVariableConstraint("b").setMaxCount(10)
    )

    matchAndAssert(
      "IF Ignore brackets - Var with count",
      content,
      """for { i <- 1 until x; if i % 2 == 0; i2 = i } {
        | $b$
        | a
        |}
        |""",
      _.addNewVariableConstraint("b").setMinCount(0)
    )
  }

  def testYieldIsTested(): Unit = {
    val content =
      """<match="AA">for { i <- 1 until x; if i % 2 == 0; i2 = i } yield i2</match="AA">
        |<match="AB">for { i <- 1 until x; if i % 2 == 0; i2 = i } i2</match="AB">
        |"""
    matchAndAssert(
      "With yield",
      clearMarker(content, Set("AA")), "for { i <- 1 until x; if i % 2 == 0; i2 = i } yield $a$",
    )
    matchAndAssert(
      "Without yield",
      clearMarker(content, Set("AB")), "for { i <- 1 until x; if i % 2 == 0; i2 = i } $a$",
    )
  }

  def testVariablesHeader(): Unit = {
    val content =
      """<match="AA">for { i <- 1 until x; if i % 2 == 0; i2 = i } i2</match="AA">
        |<match="AB">for { i <- 1 until x; if i % 2 == 0; if i % 2 == 0; i2 = i } i2</match="AB">
        |<match="AC">for { i <- 1 until x; if i % 2 == 1; if i % 2 == 0; i2 = i } i2</match="AC">
        |"""
    matchAndAssert(
      "Match generator",
      clearMarker(content, Set("AA")), "for { $b$; if i % 2 == 0; i2 = i } $a$",
    )
    matchAndAssert(
      "Match guard",
      clearMarker(content, Set("AA")), "for { i <- 1 until x; $b$; i2 = i } $a$",
    )
    matchAndAssert(
      "Match for binding",
      clearMarker(content, Set("AA")), "for { i <- 1 until x; if i % 2 == 0; $b$ } $a$",
    )
    matchAndAssert(
      "Match all 1",
      clearMarker(content, Set("AA")), "for { $b$ } $a$",
      _.addNewVariableConstraint("b").setMaxCount(3)
    )
    matchAndAssert(
      "Match all 2",
      content, "for { $b$ } $a$",
      _.addNewVariableConstraint("b").setMaxCount(4)
    )
    matchAndAssert(
      "Match part 1",
      clearMarker(content, Set("AA", "AB")), "for { i <- 1 until x; if i % 2 == 0; $b$ } $a$",
      _.addNewVariableConstraint("b").setMaxCount(10)
    )
    matchAndAssert(
      "Match part 2",
      content, "for { $b$; if i % 2 == 0; i2 = i } $a$",
      _.addNewVariableConstraint("b").setMaxCount(10)
    )
    matchAndAssert(
      "Match part same",
      clearMarker(content, Set("AB")), "for { i <- 1 until x; $b$; $b$; i2 = i } $a$",
    )
    matchAndAssert(
      "Match part same",
      clearMarker(content, Set("AB", "AC")), "for { i <- 1 until x; $b$; $c$; i2 = i } $a$",
    )
  }
}
