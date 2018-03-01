package org.jetbrains.plugins.scala
package format

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.junit.Assert._

/**
 * Pavel Fatin
 */

class InterpolatedStringFormatterTest extends SimpleTestCase {
  def testEmpty() {
    assertEquals("", format())
  }

  def testText() {
    assertEquals("foo", format(Text("foo")))
  }

  def testEscapeChar() {
    val text = Text("\n")
    assertEquals("\\n", format(text))
    assertEquals(quoted("\n", multiline = true), formatFull(text))
  }

  def testSlash() {
    assertEquals("\\\\", format(Text("\\")))
  }

  def testDollar() {
    assertEquals("$$", format(Text("$")))
    assertEquals(quoted("$"), formatFull(Text("$")))

    val parts = Seq(Text("$ "), Injection(exp("amount"), None))
    assertEquals("$$ $amount", format(parts: _*))
    assertEquals(quoted("$$ $amount", prefix = "s"), formatFull(parts: _*))
  }

  def testPlainExpression() {
    val injection = Injection(exp("foo"), None)
    assertEquals("$foo", format(injection))
    assertEquals(quoted("$foo", prefix = "s"), formatFull(injection))
  }

  def testExpressionWithDispensableFormat() {
    val injection = Injection(exp("foo"), Some(Specifier(null, "%d")))
    assertEquals(quoted("$foo", prefix = "s"), formatFull(injection))
  }

  def testExpressionWithMadatoryFormat() {
    val injection = Injection(exp("foo"), Some(Specifier(null, "%2d")))
    assertEquals(quoted("$foo%2d", prefix = "f"), formatFull(injection))
  }

  def testPlainLiteral() {
    assertEquals(quoted("123"), formatFull(Injection(exp("123"), None)))
  }

  def testLiteralWithDispensableFormat() {
    val injection = Injection(exp("123"), Some(Specifier(null, "%d")))
    assertEquals(quoted("123"), formatFull(injection))
  }

  def testLiteralWithMadatoryFormat() {
    val injection = Injection(exp("123"), Some(Specifier(null, "%2d")))
    assertEquals(quoted("${123}%2d", prefix = "f"), formatFull(injection))
  }

  def testPlainComplexExpression() {
    val injection = Injection(exp("foo.bar"), None)
    assertEquals(quoted("${foo.bar}", prefix = "s"), formatFull(injection))
  }

  def testComplexExpressionWithDispensableFormat() {
    val injection = Injection(exp("foo.bar"), Some(Specifier(null, "%d")))
    assertEquals(quoted("${foo.bar}", prefix = "s"), formatFull(injection))
  }

  def testComplexExpressionWithMadatoryFormat() {
    val injection = Injection(exp("foo.bar"), Some(Specifier(null, "%2d")))
    assertEquals(quoted("${foo.bar}%2d", prefix = "f"), formatFull(injection))
  }

  def testPlainBlockExpression() {
    val injection = Injection(exp("{foo.bar}"), None)
    assertEquals(quoted("${foo.bar}", prefix = "s"), formatFull(injection))
  }

  def testBlockExpressionWithDispensableFormat() {
    val injection = Injection(exp("{foo.bar}"), Some(Specifier(null, "%d")))
    assertEquals(quoted("${foo.bar}", prefix = "s"), formatFull(injection))
  }

  def testBlockExpressionWithMadatoryFormat() {
    val injection = Injection(exp("{foo.bar}"), Some(Specifier(null, "%2d")))
    assertEquals(quoted("${foo.bar}%2d", prefix = "f"), formatFull(injection))
  }

  def testMixedParts() {
    val parts = Seq(Text("foo "), Injection(exp("exp"), None), Text(" bar"))
    assertEquals(quoted("foo $exp bar", prefix = "s"), formatFull(parts: _*))
  }

  def testLiterals() {
    val stringLiteral = exp(quoted("foo"))
    assertEquals(quoted("foo"), formatFull(Injection(stringLiteral, None)))

    val longLiteralInjection = Injection(exp("123L"), None)
    assertEquals(quoted("123"), formatFull(longLiteralInjection))

    val booleanLiteralInjection = Injection(exp("true"), None)
    assertEquals(quoted("true"), formatFull(booleanLiteralInjection))
  }

  def testOther() {
    assertEquals("", format(UnboundExpression(exp("foo"))))
  }

  private def format(parts: StringPart*): String = {
    InterpolatedStringFormatter.formatContent(parts)
  }

  //with prefix and quotes
  private def formatFull(parts: StringPart*): String = {
    InterpolatedStringFormatter.format(parts)
  }

  private def quoted(s: String, multiline: Boolean = false, prefix: String = "") = {
    val quote = if (multiline) "\"\"\"" else "\""
    s"$prefix$quote$s$quote"
  }

  private def exp(s: String): ScExpression = {
    createExpressionFromText(s)(fixture.getProject)
  }
}
