package org.jetbrains.plugins.scala
package format

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.junit.Assert._

class InterpolatedStringFormatterTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testEmpty(): Unit = {
    assertEquals("", formatS())
    assertEquals("", formatF())
    assertEquals("", formatRaw())
  }

  def testText(): Unit = {
    assertEquals("foo", formatS(Text("foo")))
    assertEquals("foo", formatF(Text("foo")))
    assertEquals("foo", formatRaw(Text("foo")))
  }

  def testEscapeChar(): Unit = {
    val text = Text("a \\ \n \t b")
    assertEquals("a \\\\ \\n \\t b", formatS(text))
    assertEquals(quoted("a \\ \n \t b", multiline = true), formatFull(text))
  }

  def testSlash(): Unit = {
    assertEquals("\\\\ \\\\\\\\", formatS(Text("\\ \\\\")))
    assertEquals("\\\\ \\\\\\\\", formatF(Text("\\ \\\\")))
    assertEquals("\\ \\\\", formatRaw(Text("\\ \\\\")))
  }

  def testDollar(): Unit = {
    assertEquals("$$", formatS(Text("$")))
    assertEquals(quoted("$"), formatFull(Text("$")))

    val parts = Seq(Text("$ "), Injection(exp("amount"), None))
    assertEquals("$$ $amount", formatS(parts: _*))
    assertEquals(quoted("$$ $amount", prefix = "s"), formatFull(parts: _*))
  }

  def testPlainExpression(): Unit = {
    val injection = Injection(exp("foo"), None)
    assertEquals("$foo", formatS(injection))
    assertEquals(quoted("$foo", prefix = "s"), formatFull(injection))
  }

  def testExpressionWithDispensableFormat(): Unit = {
    val injection = Injection(exp("foo"), Some(Specifier(null, "%d")))
    assertEquals(quoted("$foo", prefix = "s"), formatFull(injection))
  }

  def testExpressionWithMadatoryFormat(): Unit = {
    val injection = Injection(exp("foo"), Some(Specifier(null, "%2d")))
    assertEquals(quoted("$foo%2d", prefix = "f"), formatFull(injection))
  }

  def testPlainLiteral(): Unit = {
    assertEquals(quoted("123"), formatFull(Injection(exp("123"), None)))
  }

  def testLiteralWithDispensableFormat(): Unit = {
    val injection = Injection(exp("123"), Some(Specifier(null, "%d")))
    assertEquals(quoted("123"), formatFull(injection))
  }

  def testLiteralWithMadatoryFormat(): Unit = {
    val injection = Injection(exp("123"), Some(Specifier(null, "%2d")))
    assertEquals(quoted("${123}%2d", prefix = "f"), formatFull(injection))
  }

  def testPlainComplexExpression(): Unit = {
    val injection = Injection(exp("foo.bar"), None)
    assertEquals(quoted("${foo.bar}", prefix = "s"), formatFull(injection))
  }

  def testComplexExpressionWithDispensableFormat(): Unit = {
    val injection = Injection(exp("foo.bar"), Some(Specifier(null, "%d")))
    assertEquals(quoted("${foo.bar}", prefix = "s"), formatFull(injection))
  }

  def testComplexExpressionWithMadatoryFormat(): Unit = {
    val injection = Injection(exp("foo.bar"), Some(Specifier(null, "%2d")))
    assertEquals(quoted("${foo.bar}%2d", prefix = "f"), formatFull(injection))
  }

  def testPlainBlockExpression(): Unit = {
    val injection = Injection(exp("{foo.bar}"), None)
    assertEquals(quoted("${foo.bar}", prefix = "s"), formatFull(injection))
  }

  def testBlockExpressionWithDispensableFormat(): Unit = {
    val injection = Injection(exp("{foo.bar}"), Some(Specifier(null, "%d")))
    assertEquals(quoted("${foo.bar}", prefix = "s"), formatFull(injection))
  }

  def testBlockExpressionWithMandatoryFormat(): Unit = {
    val injection = Injection(exp("{foo.bar}"), Some(Specifier(null, "%2d")))
    assertEquals(quoted("${foo.bar}%2d", prefix = "f"), formatFull(injection))
  }

  def testMixedParts(): Unit = {
    val parts = Seq(Text("foo "), Injection(exp("exp"), None), Text(" bar"))
    assertEquals(quoted("foo $exp bar", prefix = "s"), formatFull(parts: _*))
  }

  def testLiterals(): Unit = {
    val stringLiteral = exp(quoted("foo"))
    assertEquals(quoted("foo"), formatFull(Injection(stringLiteral, None)))

    val longLiteralInjection = Injection(exp("123L"), None)
    assertEquals(quoted("123"), formatFull(longLiteralInjection))

    val booleanLiteralInjection = Injection(exp("true"), None)
    assertEquals(quoted("true"), formatFull(booleanLiteralInjection))
  }

  def testOther(): Unit = {
    assertEquals("", formatS(UnboundExpression(exp("foo"))))
  }

  private def formatS(parts: StringPart*): String =
    InterpolatedStringFormatter.formatContent(parts, "s", toMultiline = false)
  private def formatF(parts: StringPart*): String =
    InterpolatedStringFormatter.formatContent(parts, "f", toMultiline = false)
  private def formatRaw(parts: StringPart*): String =
    InterpolatedStringFormatter.formatContent(parts, "raw", toMultiline = false)

  //with prefix and quotes
  private def formatFull(parts: StringPart*): String = {
    InterpolatedStringFormatter.format(parts)
  }

  private def quoted(content: String, multiline: Boolean = false, prefix: String = "") = {
    val quote = if (multiline) "\"\"\"" else "\""
    s"$prefix$quote$content$quote"
  }

  private def exp(s: String): ScExpression = {
    createExpressionFromText(s)(getProject)
  }
}
