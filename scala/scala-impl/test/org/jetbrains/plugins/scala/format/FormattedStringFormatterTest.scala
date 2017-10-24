package org.jetbrains.plugins.scala
package format

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.junit.Assert._

/**
 * Pavel Fatin
 */

class FormattedStringFormatterTest extends SimpleTestCase {
  def testEmpty() {
    assertEquals(call("", ""), format())
  }

  def testText() {
    assertEquals(call("foo", ""), format(Text("foo")))
  }

  def testEscapeChar() {
    assertEquals(call("\\n", ""), format(Text("\n")))
  }

  def testSlash() {
    assertEquals(call("\\\\", ""), format(Text("\\")))
  }

  def testPlainExpression() {
    assertEquals(call("%s", "foo"), format(Injection(exp("foo"), None)))
  }

  def testExpressionWithDispensableFormat() {
    assertEquals(call("%d", "foo"), format(Injection(exp("foo"), Some(Specifier(null, "%d")))))
  }

  def testExpressionWithMadatoryFormat() {
    assertEquals(call("%2d", "foo"), format(Injection(exp("foo"), Some(Specifier(null, "%2d")))))
  }

  def testPlainLiteral() {
    assertEquals(call("123", ""), format(Injection(exp("123"), None)))
  }

  def testLiteralWithDispensableFormat() {
    assertEquals(call("%d", "123"), format(Injection(exp("123"), Some(Specifier(null, "%d")))))
  }

  def testLiteralWithMadatoryFormat() {
    assertEquals(call("%2d", "123"), format(Injection(exp("123"), Some(Specifier(null, "%2d")))))
  }

  def testPlainComplexExpression() {
    assertEquals(call("%s", "foo.bar"), format(Injection(exp("foo.bar"), None)))
  }

  def testComplexExpressionWithDispensableFormat() {
    assertEquals(call("%d", "foo.bar"), format(Injection(exp("foo.bar"), Some(Specifier(null, "%d")))))
  }

  def testComplexExpressionWithMadatoryFormat() {
    assertEquals(call("%2d", "foo.bar"), format(Injection(exp("foo.bar"), Some(Specifier(null, "%2d")))))
  }

  def testPlainBlockExpression() {
    assertEquals(call("%s", "foo.bar"), format(Injection(exp("{foo.bar}"), None)))
  }

  def testBlockExpressionWithDispensableFormat() {
    assertEquals(call("%d", "foo.bar"), format(Injection(exp("{foo.bar}"), Some(Specifier(null, "%d")))))
  }

  def testBlockExpressionWithMadatoryFormat() {
    assertEquals(call("%2d", "foo.bar"), format(Injection(exp("{foo.bar}"), Some(Specifier(null, "%2d")))))
  }

  def testPlainComplexBlockExpression() {
    assertEquals(call("%s", "{null; foo.bar}"), format(Injection(exp("{null; foo.bar}"), None)))
  }

  def testComplexBlockExpressionWithDispensableFormat() {
    assertEquals(call("%d", "{null; foo.bar}"), format(Injection(exp("{null; foo.bar}"), Some(Specifier(null, "%d")))))
  }

  def testComplexBlockExpressionWithMadatoryFormat() {
    assertEquals(call("%2d", "{null; foo.bar}"), format(Injection(exp("{null; foo.bar}"), Some(Specifier(null, "%2d")))))
  }

  def testMixedParts() {
    assertEquals(call("foo %s bar %s", "a, b"), format(Text("foo "), Injection(exp("a"), None), Text(" bar "), Injection(exp("b"), None)))
  }

  def testLiterals() {
    assertEquals(call("foo", ""), format(Injection(exp('"' + "foo" + '"'), None)))
    assertEquals(call("123", ""), format(Injection(exp("123L"), None)))
    assertEquals(call("true", ""), format(Injection(exp("true"), None)))
  }

  def testOther() {
    assertEquals(call("", ""), format(UnboundExpression(exp("foo"))))
  }

  private def format(parts: StringPart*): String = {
    FormattedStringFormatter.format(parts)
  }

  def call(formatter: String, arguments: String) =
    '"' + formatter + '"' + ".format(%s)".format(arguments)

  private def exp(s: String): ScExpression = {
    createExpressionFromText(s)
  }
}
