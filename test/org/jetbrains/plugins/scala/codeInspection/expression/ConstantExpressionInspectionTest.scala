package org.jetbrains.plugins.scala.codeInspection.expression

import org.jetbrains.plugins.scala.base.InspectionTestCase

class ConstantExpressionInspectionTest extends InspectionTestCase[ConstantExpressionInspection] {
  def testInfixExpression() {
    assertHighlights("1 + 2", Highlight(0, 5, "Can be simplified to '3'"))
    assertHighlights("3 - 2", Highlight(0, 5, "Can be simplified to '1'"))
    assertHighlights("2 * 3", Highlight(0, 5, "Can be simplified to '6'"))
    assertHighlights("6 / 3", Highlight(0, 5, "Can be simplified to '2'"))
  }

  def testLiteral() {
    assertHighlights("1")
  }

  def testUnknownOperator() {
    assertHighlights("1 % 2")
  }

  def testComplexExpression() {
    assertHighlights("1 + 2 * 3", Highlight(0, 9, "Can be simplified to '7'"))
  }

  def testExpressionInParentheses() {
    assertHighlights("(1 + 2)", Highlight(0, 7, "Can be simplified to '3'"))
  }

  def testLiteralInParentheses() {
    assertHighlights("(1)")
  }

  def testComplexExpressionWithParentheses() {
    assertHighlights("1 * (2 + 3)", Highlight(0, 11, "Can be simplified to '5'"))
  }

  def testExpressionWithReference() {
    assertHighlights("val v = 1; v + 2", Highlight(11, 16, "Can be simplified to '3'"))
  }

  def testReference() {
    assertHighlights("val v = 1; v")
  }
}
