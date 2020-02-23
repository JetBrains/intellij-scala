package org.jetbrains.plugins.scala
package format

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions.ElementText
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral

/**
 * Pavel Fatin
 */

class InterpolatedStringParserTest extends SimpleTestCase {
  def testEmpty(): Unit = {
    assertMatches(parse("")) {
      case Nil =>
    }
  }

  def testText(): Unit = {
    assertMatches(parse("foo")) {
      case Text("foo") :: Nil =>
    }
  }

  def testEscapeChar(): Unit = {
    assertMatches(parse("\\n")) {
      case Text("\n") :: Nil =>
    }
  }

  def testEscapeCharInMultilineString(): Unit = {
    assertMatches(parse("\n", multiline = true)) {
      case Text("\n") :: Nil =>
    }
  }

  def testDollarEscapeChar(): Unit = {
    assertMatches(parse("$$")) {
      case Text("$") :: Nil =>
    }
  }

  def testExpression(): Unit = {
    assertMatches(parse("$foo")) {
      case Injection(ElementText("foo"), None) :: Nil =>
    }
  }

  def testBlockExpression(): Unit = {
    assertMatches(parse("${foo.bar}")) {
      case Injection(ElementText("foo.bar"), None) :: Nil =>
    }
  }

  def testComplexBlockExpression(): Unit = {
    assertMatches(parse("${null; foo}")) {
      case Injection(ElementText("{null; foo}"), None) :: Nil =>
    }
  }

  def testFormattedExpression(): Unit = {
    assertMatches(parse("$foo%d")) {
      case Injection(ElementText("foo"), Some(Specifier(Span(_, 0, 2), "%d"))) :: Nil =>
    }
  }

  def testExpressionWithSeparatedFormatter(): Unit = {
    assertMatches(parse("$foo %d")) {
      case Injection(ElementText("foo"), None) :: Text(" ") :: Injection(ElementText("\"%\""), None) :: Text("d") :: Nil =>
    }
  }

  def testFormattedBlockExpression(): Unit = {
    assertMatches(parse("${foo}%d")) {
      case Injection(ElementText("foo"), Some(Specifier(Span(_, 0, 2), "%d"))) :: Nil =>
    }
  }

  def testFormattedComplexBlockExpression(): Unit = {
    assertMatches(parse("${null; foo}%d")) {
      case Injection(ElementText("{null; foo}"), Some(Specifier(Span(_, 0, 2), "%d"))) :: Nil =>
    }
  }

  def testMixed(): Unit = {
    assertMatches(parse("foo $exp ${it.name}%2d bar")) {
      case Text("foo ") ::
              Injection(ElementText("exp"), None) ::
              Text(" ") ::
              Injection(ElementText("it.name"), Some(Specifier(Span(_, 0, 3), "%2d"))) ::
              Text(" bar") ::
              Nil =>
    }
  }

  def testUnformattedWithSpecifiers(): Unit = {
    assertMatches(parse("$foo%d", formatted = false)) {
      case Injection(ElementText("foo"), None) :: Injection(ElementText("\"%\""), None) :: Text("d") :: Nil =>
    }
  }

  def testMultiline(): Unit = {
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
