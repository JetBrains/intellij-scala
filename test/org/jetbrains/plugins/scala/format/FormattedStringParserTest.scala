package org.jetbrains.plugins.scala
package format

import base.SimpleTestCase
import lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi.PsiManager
import extensions.ElementText
import lang.psi.api.base.ScLiteral

/**
 * Pavel Fatin
 */

class FormattedStringParserTest extends SimpleTestCase {
  def testEmpty() {
    assertMatches(parse("")) {
      case Nil =>
    }
  }

  def testPlainText() {
    assertMatches(parse("foo")) {
      case Text("foo") :: Nil =>
    }
  }

  def testFormatSpecifierWithArgument() {
    assertMatches(parse("%d", 1)) {
      case Injection(ElementText("1"), Some(Specifier(Span(_, 1, 3), "%d"))) :: Nil =>
    }
  }

  def testFormatSpecifierWithoutArgument() {
    assertMatches(parse("%d")) {
      case UnboundSpecifier(Specifier(Span(_, 1, 3), "%d")) :: Nil =>
    }
    assertMatches(parse("foo %d")) {
      case Text("foo ") :: UnboundSpecifier(Specifier(Span(_, 5, 7), "%d")) :: Nil =>
    }
  }

  def testArgumentWithoutFormatSpecifier() {
    assertMatches(parse("", 1)) {
      case UnboundExpression(ElementText("1")) :: Nil =>
    }
  }

  def testTextThanFormatSpecifier() {
    assertMatches(parse("foo %d", 1)) {
      case Text("foo ") :: Injection(ElementText("1"), Some(Specifier(Span(_, 5, 7), "%d"))) :: Nil =>
    }
  }

  def testFormatSpecifierThanText() {
    assertMatches(parse("%d foo", 1)) {
      case Injection(ElementText("1"), Some(Specifier(Span(_, 1, 3), "%d")))
              :: Text(" foo") :: Nil =>
    }
  }

  def testMixedFormatString() {
    assertMatches(parse("A%dB%sC%cD", 1, 2, 3)) {
      case Text("A") ::
              Injection(ElementText("1"), Some(Specifier(Span(_, 2, 4), "%d"))) ::
              Text("B") ::
              Injection(ElementText("2"), Some(Specifier(Span(_, 5, 7), "%s"))) ::
              Text("C") ::
              Injection(ElementText("3"), Some(Specifier(Span(_, 8, 10), "%c"))) ::
              Text("D") ::
              Nil =>
    }

    assertMatches(parse("%dA%sB%c", 1, 2, 3)) {
      case Injection(ElementText("1"), Some(Specifier(Span(_, 1, 3), "%d"))) ::
              Text("A") ::
              Injection(ElementText("2"), Some(Specifier(Span(_, 4, 6), "%s"))) ::
              Text("B") ::
              Injection(ElementText("3"), Some(Specifier(Span(_, 7, 9), "%c"))) ::
              Nil =>
    }
  }

  def testFormatSpecifierWithPositionalArgument() {
    assertMatches(parse("%2$d", 3, 5)) {
      case Injection(ElementText("5"), Some(Specifier(Span(_, 1, 5), "%d"))) ::
              UnboundExpression(ElementText("3")) :: Nil =>
    }
  }

  def testFormatSpecifierWithOutOfBoundPositionalArgument() {
    assertMatches(parse("%3$d", 3, 5)) {
      case UnboundPositionalSpecifier(Specifier(Span(_, 1, 5),"%d"), 3) :: UnboundExpression(ElementText("3")) ::
              UnboundExpression(ElementText("5")) :: Nil =>
    }
    assertMatches(parse("foo %1$d")) {
      case Text("foo ") ::
              UnboundPositionalSpecifier(Specifier(Span(_, 5, 9), "%d"), 1) :: Nil =>
    }
  }

  def testPositionalArgumentThanOrdinaryArgument() {
    assertMatches(parse("%2$d%s", 3, 5)) {
      case Injection(ElementText("5"), Some(Specifier(Span(_, 1, 5), "%d"))) ::
              Injection(ElementText("3"), Some(Specifier(Span(_, 5, 7), "%s"))) :: Nil =>
    }
  }

  def testOrdinaryArgumentArgumentThanPositional() {
    assertMatches(parse("%d%1$s", 3, 5)) {
      case Injection(ElementText("3"), Some(Specifier(Span(_, 1, 3), "%d"))) ::
              Injection(ElementText("3"), Some(Specifier(Span(_, 3, 7), "%s"))) ::
              UnboundExpression(ElementText("5")) :: Nil =>
    }
  }

  private def parse(formatString: String, arguments: Int*): List[StringPart] = {
    val manager = PsiManager.getInstance(fixture.getProject)
    val expressions = arguments.map(it => ScalaPsiElementFactory.createExpressionFromText(it.toString, manager))
    val literal = ScalaPsiElementFactory.parseElement('"' + formatString + '"', manager).asInstanceOf[ScLiteral]
    FormattedStringParser.parseFormatCall(literal, expressions).toList
  }
}
