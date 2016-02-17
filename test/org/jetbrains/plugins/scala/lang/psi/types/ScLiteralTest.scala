package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
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
    assertTypeIs("1L", api.Long)
  }

  def testFloatLiteral() {
    assertTypeIs("1f", api.Float)
    assertTypeIs("1F", api.Float)
  }

  def testDoubleLiteral() {
    assertTypeIs("1d", api.Double)
    assertTypeIs("1D", api.Double)
  }

  def testCharLiteral() {
    assertTypeIs("'c'", Char)
  }

  def testBooleanLiteral() {
    assertTypeIs("true", Boolean)
    assertTypeIs("false", api.Boolean)
  }

  private def assertTypeIs(code: String, expectation: ScType) {
    val exp = code.parse[ScExpression]
    val t = exp.getType(TypingContext.empty).get
    Assert.assertEquals(expectation, t)
  }
}