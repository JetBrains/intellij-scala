package org.jetbrains.plugins.scala.lang.psi.types

import junit.framework.Assert
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

/**
 * Pavel Fatin
 */

class ScLiteralTest extends SimpleTestCase {
  def testNullLiteral() {
    assertTypeIs("null", types.Null)
  }

  def testIntLiteral() {
    assertTypeIs("1", types.Int)
  }

  def testLongLiteral() {
    assertTypeIs("1l", types.Long)
    assertTypeIs("1L", types.Long)
  }

  def testFloatLiteral() {
    assertTypeIs("1f", types.Float)
    assertTypeIs("1F", types.Float)
  }

  def testDoubleLiteral() {
    assertTypeIs("1d", types.Double)
    assertTypeIs("1D", types.Double)
  }

  def testCharLiteral() {
    assertTypeIs("'c'", types.Char)
  }

  def testBooleanLiteral() {
    assertTypeIs("true", types.Boolean)
    assertTypeIs("false", types.Boolean)
  }

  private def assertTypeIs(code: String, expectation: ScType) {
    val exp = code.parse[ScExpression]
    val t = exp.getType(TypingContext.empty).get
    Assert.assertEquals(expectation, t)
  }
}