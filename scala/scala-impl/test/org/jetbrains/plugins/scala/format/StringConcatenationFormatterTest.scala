package org.jetbrains.plugins.scala
package format

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.junit.Assert._

/**
 * Pavel Fatin
 */

class StringConcatenationFormatterTest extends SimpleTestCase {
  def testEmpty(): Unit = {
    assertEquals("\"\"", format())
  }

  def testText(): Unit = {
    assertEquals("\"foo\"", format(Text("foo")))
  }

  def testEscapeChar(): Unit = {
    assertEquals("\"\\n\"", format(Text("\n")))
  }

  def testSlash(): Unit = {
    assertEquals("\"\\\\\"", format(Text("\\")))
  }

  def testPlainExpression(): Unit = {
    assertEquals("foo", format(Injection(exp("foo"), None)))
  }

  def testExpressionWithDispensableFormat(): Unit = {
    assertEquals("foo", format(Injection(exp("foo"), Some(Specifier(null, "%d")))))
  }

  def testExpressionWithMadatoryFormat(): Unit = {
    assertEquals("foo.formatted(\"%2d\")", format(Injection(exp("foo"), Some(Specifier(null, "%2d")))))
  }

  def testPlainLiteral(): Unit = {
    assertEquals("123", format(Injection(exp("123"), None)))
  }

  def testLiteralWithDispensableFormat(): Unit = {
    assertEquals("123", format(Injection(exp("123"), Some(Specifier(null, "%d")))))
  }

  def testLiteralWithMadatoryFormat(): Unit = {
    assertEquals("123.formatted(\"%2d\")", format(Injection(exp("123"), Some(Specifier(null, "%2d")))))
  }

  def testPlainComplexExpression(): Unit = {
    assertEquals("foo.bar", format(Injection(exp("foo.bar"), None)))
  }

  def testComplexExpressionWithDispensableFormat(): Unit = {
    assertEquals("foo.bar", format(Injection(exp("foo.bar"), Some(Specifier(null, "%d")))))
  }

  def testComplexExpressionWithMadatoryFormat(): Unit = {
    assertEquals("foo.bar.formatted(\"%2d\")", format(Injection(exp("foo.bar"), Some(Specifier(null, "%2d")))))
  }

  def testPlainBlockExpression(): Unit = {
    assertEquals("foo.bar", format(Injection(exp("{foo.bar}"), None)))
  }

  def testBlockExpressionWithDispensableFormat(): Unit = {
    assertEquals("foo.bar", format(Injection(exp("{foo.bar}"), Some(Specifier(null, "%d")))))
  }

  def testBlockExpressionWithMadatoryFormat(): Unit = {
    assertEquals("foo.bar.formatted(\"%2d\")", format(Injection(exp("{foo.bar}"), Some(Specifier(null, "%2d")))))
  }

  def testPlainComplexBlockExpression(): Unit = {
    assertEquals("{null; foo.bar}", format(Injection(exp("{null; foo.bar}"), None)))
  }

  def testComplexBlockExpressionWithDispensableFormat(): Unit = {
    assertEquals("{null; foo.bar}", format(Injection(exp("{null; foo.bar}"), Some(Specifier(null, "%d")))))
  }

  def testComplexBlockExpressionWithMadatoryFormat(): Unit = {
    assertEquals("{null; foo.bar}.formatted(\"%2d\")", format(Injection(exp("{null; foo.bar}"), Some(Specifier(null, "%2d")))))
  }

  def testMixedParts(): Unit = {
    assertEquals("\"foo \" + exp + \" bar\"", format(Text("foo "), Injection(exp("exp"), None), Text(" bar")))
  }

  def testStringLiteral(): Unit = {
    assertEquals("\"foo\"", format(Injection(exp('"' + "foo" + '"'), None)))
    assertEquals("123L", format(Injection(exp("123L"), None)))
    assertEquals("true", format(Injection(exp("true"), None)))
  }

  def testOther(): Unit = {
    assertEquals("", format(UnboundExpression(exp("foo"))))
  }

  private def format(parts: StringPart*): String = {
    StringConcatenationFormatter.format(parts)
  }

  private def exp(s: String): ScExpression = {
    createExpressionFromText(s)
  }
}
