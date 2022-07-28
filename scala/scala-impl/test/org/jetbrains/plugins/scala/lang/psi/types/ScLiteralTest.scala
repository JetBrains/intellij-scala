package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.junit.Assert

class ScLiteralTest extends SimpleTestCase {
  def testNullLiteral(): Unit = {
    assertTypeIs("null", Null)
  }

  def testIntLiteral(): Unit = {
    assertTypeIs("1", Int)
  }

  def testLongLiteral(): Unit = {
    assertTypeIs("1l", Long)
    assertTypeIs("1L", Long)
  }

  def testFloatLiteral(): Unit = {
    assertTypeIs("1f", Float)
    assertTypeIs("1F", Float)
  }

  def testDoubleLiteral(): Unit = {
    assertTypeIs("1d", Double)
    assertTypeIs("1D", Double)
  }

  def testCharLiteral(): Unit = {
    assertTypeIs("'c'", Char)
  }

  def testBooleanLiteral(): Unit = {
    assertTypeIs("true", Boolean)
    assertTypeIs("false", Boolean)
  }

  private def assertTypeIs(code: String, expectation: ScType): Unit = {
    val exp = code.parse[ScExpression]
    val actual = exp.`type`().get
    Assert.assertEquals(expectation, actual)
  }
}