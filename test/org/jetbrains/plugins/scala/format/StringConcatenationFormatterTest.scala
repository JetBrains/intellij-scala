package org.jetbrains.plugins.scala
package format

import base.SimpleTestCase
import lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi.PsiManager
import org.junit.Assert._
import lang.psi.api.expr.ScExpression

/**
 * Pavel Fatin
 */

class StringConcatenationFormatterTest extends SimpleTestCase {
  def testEmpty() {
    assertEquals("\"\"", format())
  }

  def testText() {
    assertEquals("\"foo\"", format(Text("foo")))
  }

  def testPlainExpression() {
    assertEquals("foo", format(Injection(exp("foo"), None)))
  }

  def testExpressionWithDispensableFormat() {
    assertEquals("foo", format(Injection(exp("foo"), Some(Specifier(null, "%d")))))
  }

  def testExpressionWithMadatoryFormat() {
    assertEquals("foo.formatted(\"%2d\")", format(Injection(exp("foo"), Some(Specifier(null, "%2d")))))
  }

  def testPlainLiteral() {
    assertEquals("123", format(Injection(exp("123"), None)))
  }

  def testLiteralWithDispensableFormat() {
    assertEquals("123", format(Injection(exp("123"), Some(Specifier(null, "%d")))))
  }

  def testLiteralWithMadatoryFormat() {
    assertEquals("123.formatted(\"%2d\")", format(Injection(exp("123"), Some(Specifier(null, "%2d")))))
  }

  def testPlainComplexExpression() {
    assertEquals("foo.bar", format(Injection(exp("foo.bar"), None)))
  }

  def testComplexExpressionWithDispensableFormat() {
    assertEquals("foo.bar", format(Injection(exp("foo.bar"), Some(Specifier(null, "%d")))))
  }

  def testComplexExpressionWithMadatoryFormat() {
    assertEquals("foo.bar.formatted(\"%2d\")", format(Injection(exp("foo.bar"), Some(Specifier(null, "%2d")))))
  }

  def testPlainBlockExpression() {
    assertEquals("foo.bar", format(Injection(exp("{foo.bar}"), None)))
  }

  def testBlockExpressionWithDispensableFormat() {
    assertEquals("foo.bar", format(Injection(exp("{foo.bar}"), Some(Specifier(null, "%d")))))
  }

  def testBlockExpressionWithMadatoryFormat() {
    assertEquals("foo.bar.formatted(\"%2d\")", format(Injection(exp("{foo.bar}"), Some(Specifier(null, "%2d")))))
  }

  def testPlainComplexBlockExpression() {
    assertEquals("{null; foo.bar}", format(Injection(exp("{null; foo.bar}"), None)))
  }

  def testComplexBlockExpressionWithDispensableFormat() {
    assertEquals("{null; foo.bar}", format(Injection(exp("{null; foo.bar}"), Some(Specifier(null, "%d")))))
  }

  def testComplexBlockExpressionWithMadatoryFormat() {
    assertEquals("{null; foo.bar}.formatted(\"%2d\")", format(Injection(exp("{null; foo.bar}"), Some(Specifier(null, "%2d")))))
  }

  def testMixedParts() {
    assertEquals("\"foo \" + exp + \" bar\"", format(Text("foo "), Injection(exp("exp"), None), Text(" bar")))
  }

  def testStringLiteral() {
    assertEquals("\"foo\"", format(Injection(exp('"' + "foo" + '"'), None)))
    assertEquals("123L", format(Injection(exp("123L"), None)))
    assertEquals("true", format(Injection(exp("true"), None)))
  }

  def testOther() {
    assertEquals("", format(UnboundExpression(exp("foo"))))
  }

  private def format(parts: StringPart*): String = {
    StringConcatenationFormatter.format(parts)
  }

  private def exp(s: String): ScExpression = {
    val manager = PsiManager.getInstance(fixture.getProject)
    ScalaPsiElementFactory.createExpressionFromText(s, manager)
  }
}
