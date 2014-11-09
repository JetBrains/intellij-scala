package org.jetbrains.plugins.scala
package format

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions.ElementText
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral

/**
 * Pavel Fatin
 */

class InterpolatedStringParserTest extends SimpleTestCase {
  def testEmpty() {
    assertMatches(parse("")) {
      case Nil =>
    }
  }

  def testText() {
    assertMatches(parse("foo")) {
      case Text("foo") :: Nil =>
    }
  }

  def testEscapeChar() {
    assertMatches(parse("\\n")) {
      case Text("\n") :: Nil =>
    }
  }

  def testEscapeCharInMultilineString() {
    assertMatches(parse("\n", multiline = true)) {
      case Text("\n") :: Nil =>
    }
  }

  def testDollarEscapeChar() {
    assertMatches(parse("$$")) {
      case Text("$") :: Nil =>
    }
  }

  def testExpression() {
    assertMatches(parse("$foo")) {
      case Injection(ElementText("foo"), None) :: Nil =>
    }
  }

  def testBlockExpression() {
    assertMatches(parse("${foo.bar}")) {
      case Injection(ElementText("foo.bar"), None) :: Nil =>
    }
  }

  def testComplexBlockExpression() {
    assertMatches(parse("${null; foo}")) {
      case Injection(ElementText("{null; foo}"), None) :: Nil =>
    }
  }

  def testFormattedExpression() {
    assertMatches(parse("$foo%d")) {
      case Injection(ElementText("foo"), Some(Specifier(Span(_, 0, 2), "%d"))) :: Nil =>
    }
  }

  def testExpressionWithSeparatedFormatter() {
    assertMatches(parse("$foo %d")) {
      case Injection(ElementText("foo"), None) :: Text(" ") :: Injection(ElementText("\"%\""), None) :: Text("d") :: Nil =>
    }
  }

  def testFormattedBlockExpression() {
    assertMatches(parse("${foo}%d")) {
      case Injection(ElementText("foo"), Some(Specifier(Span(_, 0, 2), "%d"))) :: Nil =>
    }
  }

  def testFormattedComplexBlockExpression() {
    assertMatches(parse("${null; foo}%d")) {
      case Injection(ElementText("{null; foo}"), Some(Specifier(Span(_, 0, 2), "%d"))) :: Nil =>
    }
  }

  def testMixed() {
    assertMatches(parse("foo $exp ${it.name}%2d bar")) {
      case Text("foo ") ::
              Injection(ElementText("exp"), None) ::
              Text(" ") ::
              Injection(ElementText("it.name"), Some(Specifier(Span(_, 0, 3), "%2d"))) ::
              Text(" bar") ::
              Nil =>
    }
  }

  def testUnformattedWithSpecifiers() {
    assertMatches(parse("$foo%d", formatted = false)) {
      case Injection(ElementText("foo"), None) :: Injection(ElementText("\"%\""), None) :: Text("d") :: Nil =>
    }
  }

  def testMultiline() {
    assertMatches(parse("$foo%d", multiline = true)) {
      case Injection(ElementText("foo"), Some(Specifier(Span(_, 0, 2), "%d"))) :: Nil =>
    }
  }

  private def parse(content: String, formatted: Boolean = true, multiline: Boolean = false): List[StringPart] = {
    val element = literal(content, formatted, multiline)
    InterpolatedStringParser.parse(element).get.toList
  }

  private def literal(s: String, formatted: Boolean, multiline: Boolean): ScInterpolatedStringLiteral = {
    val text = {
      val prefix = if (formatted) "f" else "s"
      val quote = if (multiline) "\"\"\"" else "\""
      prefix + quote + s + quote
    }
    parseText(text).getFirstChild.asInstanceOf[ScInterpolatedStringLiteral]
  }
}
