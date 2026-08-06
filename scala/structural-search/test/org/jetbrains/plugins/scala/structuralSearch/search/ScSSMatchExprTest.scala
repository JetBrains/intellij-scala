package org.jetbrains.plugins.scala.structuralSearch.search

import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchTestCase

class ScSSMatchExprTest extends ScalaStructuralSearchTestCase {

  def testBasic(): Unit = {
    val content =
      """@tailrec
        |def isPrimeMatch(x: Int)(y: Int = x - 1): Boolean = {
        |  <match="AA">(x, y) match {
        |    case (_, _) if x < 2 => false
        |    case (2, _) => true
        |    case (x, 2) => x % 2 != 0
        |    case (x, y) => x % y != 0 && isPrimeMatch(x)(y - 1)
        |  }</match="AA">
        |
        |  (x, x) match {
        |    case (_, _) if x < 2 => false
        |    case (2, _) => true
        |    case (x, 2) => x % 2 != 0
        |    case (x, y) => x % y != 0 && isPrimeMatch(x)(y - 1)
        |  }
        |  (x, y) match {
        |    case (_, _) if x < 2 => false
        |    case (4, _) => true
        |    case (x, 2) => x % 2 != 0
        |    case (x, y) => x % y != 0 && isPrimeMatch(x)(y - 1)
        |  }
        |  (x, y) match {
        |    case (_, _) if x < 2 => false
        |    case (2, _) => false
        |    case (x, 2) => x % 2 != 0
        |    case (x, y) => x % y != 0 && isPrimeMatch(x)(y - 1)
        |  }
        |  (x, y) match {
        |    case (_, _) if x < 3 => false
        |    case (2, _) => true
        |    case (x, 2) => x % 2 != 0
        |    case (x, y) => x % y != 0 && isPrimeMatch(x)(y - 1)
        |  }
        |  (x, x) match {
        |    case (_, _) if x < 3 => false
        |    case (3, _) => false
        |    case (x, 2) => x % 2 != 0
        |    case (x, y) => x % y != 0 && isPrimeMatch(x)(y - 1)
        |  }
        |}
        |"""
    matchAndAssert(
      "For Basic and anti",
      content, """(x, y) match {
                 |  case (_, _) if x < 2 => false
                 |  case (2, _) => true
                 |  case (x, 2) => x % 2 != 0
                 |  case (x, y) => x % y != 0 && isPrimeMatch(x)(y - 1)
                 |}""".stripMargin
    )
  }

  def testVariables(): Unit = {
    val content =
      """<match="AA">(x, y) match {
        |  case (_, _) if x < 2 => false
        |  case (2, _) => true
        |  case (x, 2) => x % 2 != 0
        |  case (x, y) => x % y != 0 && isPrimeMatch(x)(y - 1)
        |}</match="AA">
        |<match="AB">(x, y) match {
        |  case (_, _) if x < 2 => false
        |  case (2, _) => true
        |  case (x, 2) => x % y != 0
        |  case (x, y) => x % y != 0 && isPrimeMatch(x)(y - 1)
        |}</match="AB">
        |"""

    matchAndAssert(
      "Var in expr 1",
      clearMarker(content, Set("AA")), """($x$, y) match {
                 |  case (_, _) if x < 2 => false
                 |  case (2, _) => true
                 |  case (x, 2) => x % 2 != 0
                 |  case (x, y) => x % y != 0 && isPrimeMatch(x)(y - 1)
                 |}""".stripMargin
    )
    matchAndAssert(
      "Var in expr 2",
      clearMarker(content, Set("AA")), """$x$ match {
                 |  case (_, _) if x < 2 => false
                 |  case (2, _) => true
                 |  case (x, 2) => x % 2 != 0
                 |  case (x, y) => x % y != 0 && isPrimeMatch(x)(y - 1)
                 |}""".stripMargin
    )
    matchAndAssert(
      "Var in pattern",
      clearMarker(content, Set("AA")), """(x,y) match {
                                         |  case (_, _) if x < 2 => false
                                         |  case (2, $a$) => true
                                         |  case $b$ => x % 2 != 0
                                         |  case (x, y) => x % y != 0 && isPrimeMatch(x)(y - 1)
                                         |}""".stripMargin
    )
    matchAndAssert(
      "Var in expressions expr",
      content, """(x,y) match {
                                         |  case (_, _) if x < 2 => false
                                         |  case (2, _) => $a$
                                         |  case (x, 2) => $b$
                                         |  case (x, y) => x % y != 0 && isPrimeMatch(x)(y - 1)
                                         |}""".stripMargin
    )
  }

  def testVariableWithCount(): Unit = {
    val content =
      """<match="AA">(x, y) match {
        |  case (_, _) => true
        |  case (2, _) => true
        |  case (x, 2) => x % 2 != 0
        |  case (x, y) => x % y != 0 && isPrimeMatch(x)(y - 1)
        |}</match="AA">
        |<match="AB">(x, y) match {
        |  case (_, _) => false
        |  case (2, _) => true
        |  case (x, 2) => x % 2 != 0
        |  case (x, y) => x % y != 0 && isPrimeMatch(x)(y - 2)
        |}</match="AB">
        |"""

    matchAndAssert(
      "Match all",
      content,
      """($x$, y) match {
        |  case $p$ => $e$
        |}""".stripMargin,
      _.addNewVariableConstraint("p").setMaxCount(100)
    )
    matchAndAssert(
      "Match with start",
      clearMarker(content, Set("AA")),
      """($x$, y) match {
        |  case (_, _) => true
        |  case $p$ => $e$
        |}""".stripMargin,
      _.addNewVariableConstraint("p").setMaxCount(100)
    )
    matchAndAssert(
      "Match with end",
      clearMarker(content, Set("AB")),
      """($x$, y) match {
        |  case $p$ => $e$
        |  case (x, y) => x % y != 0 && isPrimeMatch(x)(y - 2)
        |}""".stripMargin,
      _.addNewVariableConstraint("p").setMaxCount(100)
    )
  }
}
