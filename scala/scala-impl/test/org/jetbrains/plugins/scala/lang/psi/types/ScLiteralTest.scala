package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.junit.Assert

/**
 * Pavel Fatin
 */

class ScLiteralTest extends SimpleTestCase {
  def testNullLiteral() {
    assertTypeIs("null", Null)
  }

  def testIntLiteral() {
    assertTypeIs("1", Int)
  }

  def testLongLiteral() {
    assertTypeIs("1l", Long)
    assertTypeIs("1L", Long)
  }

  def testFloatLiteral() {
    assertTypeIs("1f", Float)
    assertTypeIs("1F", Float)
  }

  def testDoubleLiteral() {
    assertTypeIs("1d", Double)
    assertTypeIs("1D", Double)
  }

  def testCharLiteral() {
    assertTypeIs("'c'", Char)
  }

  def testBooleanLiteral() {
    assertTypeIs("true", Boolean)
    assertTypeIs("false", Boolean)
  }

  private def assertTypeIs(code: String, expectation: ScType) {
    val exp = code.parse[ScExpression]
    val actual = exp.getType().get
    Assert.assertEquals(expectation, actual)
  }
}