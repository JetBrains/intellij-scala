package org.jetbrains.plugins.scala
package codeInspection.format

import base.SimpleTestCase
import codeInspection.format.Format._
import lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi.PsiManager
import extensions.ElementText

/**
 * Pavel Fatin
 */

class FormatTest extends SimpleTestCase {
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
      case Specifier(Span(0, 2), "%d", ElementText("1")) :: Nil =>
    }
  }

  def testFormatSpecifierWithoutArgument() {
    assertMatches(parse("%d")) {
      case UnboundSpecifier(Span(0, 2), "%d") :: Nil =>
    }
    assertMatches(parse("foo %d")) {
      case Text("foo ") :: UnboundSpecifier(Span(4, 6), "%d") :: Nil =>
    }
  }

  def testArgumentWithoutFormatSpecifier() {
    assertMatches(parse("", 1)) {
      case UnusedArgument(ElementText("1")) :: Nil =>
    }
  }

  def testTextThanFormatSpecifier() {
    assertMatches(parse("foo %d", 1)) {
      case Text("foo ") :: Specifier(Span(4, 6), "%d", ElementText("1")) :: Nil =>
    }
  }

  def testFormatSpecifierThanText() {
    assertMatches(parse("%d foo", 1)) {
      case Specifier(Span(0, 2), "%d", ElementText("1")) :: Text(" foo") :: Nil =>
    }
  }

  def testMixedFormatString() {
    assertMatches(parse("A%dB%sC%cD", 1, 2, 3)) {
      case Text("A") ::
              Specifier(Span(1, 3), "%d", ElementText("1")) ::
              Text("B") ::
              Specifier(Span(4, 6), "%s", ElementText("2")) ::
              Text("C") ::
              Specifier(Span(7, 9), "%c", ElementText("3")) ::
              Text("D") ::
              Nil =>
    }

    assertMatches(parse("%dA%sB%c", 1, 2, 3)) {
      case Specifier(Span(0, 2), "%d", ElementText("1")) ::
              Text("A") ::
              Specifier(Span(3, 5), "%s", ElementText("2")) ::
              Text("B") ::
              Specifier(Span(6, 8), "%c", ElementText("3")) ::
              Nil =>
    }
  }

  def testFormatSpecifierWithPositionalArgument() {
    assertMatches(parse("%2$d", 3, 5)) {
      case Specifier(Span(0, 4), "%d", ElementText("5")) :: UnusedArgument(ElementText("3")) :: Nil =>
    }
  }

  def testFormatSpecifierWithOutOfBoundPositionalArgument() {
    assertMatches(parse("%3$d", 3, 5)) {
      case UnboundPositionalSpecifier(Span(0, 4), 3, "%d") :: UnusedArgument(ElementText("3")) :: UnusedArgument(ElementText("5")) :: Nil =>
    }
    assertMatches(parse("foo %1$d")) {
      case Text("foo ") :: UnboundPositionalSpecifier(Span(4, 8), 1, "%d") :: Nil =>
    }
  }

  def testPositionalArgumentThanOrdinaryArgument() {
    assertMatches(parse("%2$d%s", 3, 5)) {
      case Specifier(Span(0, 4), "%d", ElementText("5")) :: Specifier(Span(4, 6), "%s", ElementText("3")) :: Nil =>
    }
  }

  def testOrdinaryArgumentArgumentThanPositional() {
    assertMatches(parse("%d%1$s", 3, 5)) {
      case Specifier(Span(0, 2), "%d", ElementText("3")) :: Specifier(Span(2, 6), "%s", ElementText("3")) :: UnusedArgument(ElementText("5")) :: Nil =>
    }
  }

  private def parse(formatString: String, arguments: Int*): List[Part] = {
    val manager = PsiManager.getInstance(fixture.getProject)
    val elements = arguments.map(it => ScalaPsiElementFactory.createExpressionFromText(it.toString, manager))
    parseFormatCall(formatString, elements).toList
  }
}
