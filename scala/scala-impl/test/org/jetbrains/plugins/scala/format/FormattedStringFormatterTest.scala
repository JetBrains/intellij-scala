package org.jetbrains.plugins.scala
package format

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.junit.Assert._

class FormattedStringFormatterTest extends ScalaLightCodeInsightFixtureTestAdapter {

  def testEmpty(): Unit = {
    assertEquals(call("", ""), format())
  }

  def testText(): Unit = {
    assertEquals(call("foo", ""), format(Text("foo")))
  }

  def testEscapeChar(): Unit = {
    assertEquals(call("\\n", ""), format(Text("\n")))
  }

  def testSlash(): Unit = {
    assertEquals(call("\\\\", ""), format(Text("\\")))
  }

  def testPlainExpression(): Unit = {
    assertEquals(call("%s", "foo"), format(Injection(exp("foo"), None)))
  }

  def testExpressionWithDispensableFormat(): Unit = {
    assertEquals(call("%d", "foo"), format(Injection(exp("foo"), Some(Specifier(null, "%d")))))
  }

  def testExpressionWithMadatoryFormat(): Unit = {
    assertEquals(call("%2d", "foo"), format(Injection(exp("foo"), Some(Specifier(null, "%2d")))))
  }

  def testPlainLiteral(): Unit = {
    assertEquals(call("123", ""), format(Injection(exp("123"), None)))
  }

  def testLiteralWithDispensableFormat(): Unit = {
    assertEquals(call("%d", "123"), format(Injection(exp("123"), Some(Specifier(null, "%d")))))
  }

  def testLiteralWithMadatoryFormat(): Unit = {
    assertEquals(call("%2d", "123"), format(Injection(exp("123"), Some(Specifier(null, "%2d")))))
  }

  def testPlainComplexExpression(): Unit = {
    assertEquals(call("%s", "foo.bar"), format(Injection(exp("foo.bar"), None)))
  }

  def testComplexExpressionWithDispensableFormat(): Unit = {
    assertEquals(call("%d", "foo.bar"), format(Injection(exp("foo.bar"), Some(Specifier(null, "%d")))))
  }

  def testComplexExpressionWithMadatoryFormat(): Unit = {
    assertEquals(call("%2d", "foo.bar"), format(Injection(exp("foo.bar"), Some(Specifier(null, "%2d")))))
  }

  def testPlainBlockExpression(): Unit = {
    assertEquals(call("%s", "foo.bar"), format(Injection(exp("{foo.bar}"), None)))
  }

  def testBlockExpressionWithDispensableFormat(): Unit = {
    assertEquals(call("%d", "foo.bar"), format(Injection(exp("{foo.bar}"), Some(Specifier(null, "%d")))))
  }

  def testBlockExpressionWithMandatoryFormat(): Unit = {
    assertEquals(call("%2d", "foo.bar"), format(Injection(exp("{foo.bar}"), Some(Specifier(null, "%2d")))))
  }

  def testPlainComplexBlockExpression(): Unit = {
    assertEquals(call("%s", "{null; foo.bar}"), format(Injection(exp("{null; foo.bar}"), None)))
  }

  def testComplexBlockExpressionWithDispensableFormat(): Unit = {
    assertEquals(call("%d", "{null; foo.bar}"), format(Injection(exp("{null; foo.bar}"), Some(Specifier(null, "%d")))))
  }

  def testComplexBlockExpressionWithMadatoryFormat(): Unit = {
    assertEquals(call("%2d", "{null; foo.bar}"), format(Injection(exp("{null; foo.bar}"), Some(Specifier(null, "%2d")))))
  }

  def testMixedParts(): Unit = {
    assertEquals(call("foo %s bar %s", "a, b"), format(Text("foo "), Injection(exp("a"), None), Text(" bar "), Injection(exp("b"), None)))
  }

  def testLiterals(): Unit = {
    assertEquals(call("foo", ""), format(Injection(exp("\"" + "foo" + "\""), None)))
    assertEquals(call("123", ""), format(Injection(exp("123L"), None)))
    assertEquals(call("true", ""), format(Injection(exp("true"), None)))
  }

  def testOther(): Unit = {
    assertEquals(call("", ""), format(UnboundExpression(exp("foo"))))
  }

  private def format(parts: StringPart*): String = {
    FormattedStringFormatter.format(parts)
  }

  private def call(formatter: String, arguments: String) =
    s""""$formatter".format($arguments)"""

  private def exp(s: String): ScExpression = {
    createExpressionFromText(s)(getProject)
  }
}
