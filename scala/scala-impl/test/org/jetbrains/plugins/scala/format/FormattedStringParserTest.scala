package org.jetbrains.plugins.scala
package format

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions.ElementText
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions._
import org.junit.Assert.{assertEquals, fail}

class FormattedStringParserTest extends ScalaLightCodeInsightFixtureTestAdapter {

  def testEmpty(): Unit = {
    assertMatches(parse("")) {
      case Nil =>
    }
  }

  def testPlainText(): Unit = {
    assertMatches(parse("foo")) {
      case Text("foo") :: Nil =>
    }
  }

  def testEscapeChar(): Unit = {
    assertMatches(parse("\\n")) {
      case Text("\n") :: Nil =>
    }
  }

  def testFormatSpecifierWithArgument(): Unit = {
    assertMatches(parse("%d", 1)) {
      case Injection(ElementText("1"), Some(Specifier(Span(_, 1, 3), "%d"))) :: Nil =>
    }
  }

  def testFormatSpecifierWithArgumentAfterEscapeChar(): Unit = {
    assertMatches(parse("\\n%d", 1)) {
      case Text("\n") :: Injection(ElementText("1"), Some(Specifier(Span(_, 3, 5), "%d"))) :: Nil =>
    }
  }

  def testFormatSpecifierWithoutArgument(): Unit = {
    assertMatches(parse("%d")) {
      case UnboundSpecifier(Specifier(Span(_, 1, 3), "%d")) :: Nil =>
    }
    assertMatches(parse("foo %d")) {
      case Text("foo ") :: UnboundSpecifier(Specifier(Span(_, 5, 7), "%d")) :: Nil =>
    }
  }

  def testArgumentWithoutFormatSpecifier(): Unit = {
    assertMatches(parse("", 1)) {
      case UnboundExpression(ElementText("1")) :: Nil =>
    }
  }

  def testTextThanFormatSpecifier(): Unit = {
    assertMatches(parse("foo %d", 1)) {
      case Text("foo ") :: Injection(ElementText("1"), Some(Specifier(Span(_, 5, 7), "%d"))) :: Nil =>
    }
  }

  def testFormatSpecifierThanText(): Unit = {
    assertMatches(parse("%d foo", 1)) {
      case Injection(ElementText("1"), Some(Specifier(Span(_, 1, 3), "%d")))
        :: Text(" foo") :: Nil =>
    }
  }

  def testStringWithEscapedChars(): Unit = {
    // simple string
    assertMatches(parseFull(""""a \\ %d \t b".format(42)""")) {
      case Text("a \\ ") :: Injection(ElementText("42"), Some(Specifier(Span(_, 6, 8), "%d"))) :: Text(" \t b") :: Nil =>
    }
    //multiline
    assertMatches(parseFull(s"""\"\"\"a \\\\ %d \\t b\"\"\".format(42)""")) {
      case Text("a \\\\ ") :: Injection(ElementText("42"), Some(Specifier(Span(_, 8, 10), "%d"))) :: Text(" \\t b") :: Nil =>
    }

    // interpolated + formatted is forbidden SCL-15414
    //interpolated
    assertEquals(None, parseFullOpt("""s"a \\ %d \t b".format(42)"""))
    //interpolated multiline
    assertEquals(None, parseFullOpt(s"""s\"\"\"a \\\\ %d \\t b\"\"\".format(42)"""))
    //interpolated raw
    assertEquals(None, parseFullOpt("""raw"a \\ %d \t b".format(42)"""))
    //interpolated multiline raw
    assertEquals(None, parseFullOpt(s"""raw\"\"\"a \\\\ %d \\t b\"\"\".format(42)"""))
  }

  def testMixedFormatString(): Unit = {
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

  def testFormatSpecifierWithPositionalArgument(): Unit = {
    assertMatches(parse("%2$d", 3, 5)) {
      case Injection(ElementText("5"), Some(Specifier(Span(_, 1, 5), "%d"))) ::
        UnboundExpression(ElementText("3")) :: Nil =>
    }
  }

  def testFormatSpecifierWithOutOfBoundPositionalArgument(): Unit = {
    assertMatches(parse("%3$d", 3, 5)) {
      case UnboundPositionalSpecifier(Specifier(Span(_, 1, 5),"%d"), 3) :: UnboundExpression(ElementText("3")) ::
        UnboundExpression(ElementText("5")) :: Nil =>
    }
    assertMatches(parse("foo %1$d")) {
      case Text("foo ") ::
        UnboundPositionalSpecifier(Specifier(Span(_, 5, 9), "%d"), 1) :: Nil =>
    }
  }

  def testPositionalArgumentThanOrdinaryArgument(): Unit = {
    assertMatches(parse("%2$d%s", 3, 5)) {
      case Injection(ElementText("5"), Some(Specifier(Span(_, 1, 5), "%d"))) ::
        Injection(ElementText("3"), Some(Specifier(Span(_, 5, 7), "%s"))) :: Nil =>
    }
  }

  def testOrdinaryArgumentArgumentThanPositional(): Unit = {
    assertMatches(parse("%d%1$s", 3, 5)) {
      case Injection(ElementText("3"), Some(Specifier(Span(_, 1, 3), "%d"))) ::
        Injection(ElementText("3"), Some(Specifier(Span(_, 3, 7), "%s"))) ::
        UnboundExpression(ElementText("5")) :: Nil =>
    }
  }

  def testValidSpecialFormatEscapes(): Unit = {
    val data: Seq[(String, List[StringPart])] = Seq(
      ""          -> Nil,
      " "         -> (Text(" ") :: Nil),
      " %%"       -> (Text(" ") :: SpecialFormatEscape.PercentChar :: Nil),
      " %%%%"     -> (Text(" ") :: SpecialFormatEscape.PercentChar :: SpecialFormatEscape.PercentChar :: Nil),
      " %n%%"     -> (Text(" ") :: SpecialFormatEscape.LineSeparator :: SpecialFormatEscape.PercentChar :: Nil),
      " %%%n"     -> (Text(" ") :: SpecialFormatEscape.PercentChar :: SpecialFormatEscape.LineSeparator :: Nil),
      " %% %%"    -> (Text(" ") :: SpecialFormatEscape.PercentChar :: Text(" ") :: SpecialFormatEscape.PercentChar :: Nil),
      " %% %% "   -> (Text(" ") :: SpecialFormatEscape.PercentChar :: Text(" ") :: SpecialFormatEscape.PercentChar :: Text(" ") :: Nil),
      "%% "       -> (SpecialFormatEscape.PercentChar :: Text(" ") :: Nil),
      "%% %%"     -> (SpecialFormatEscape.PercentChar :: Text(" ") :: SpecialFormatEscape.PercentChar :: Nil),
      "%n %%"     -> (SpecialFormatEscape.LineSeparator :: Text(" ") :: SpecialFormatEscape.PercentChar :: Nil),
      "%% %n"     -> (SpecialFormatEscape.PercentChar :: Text(" ") :: SpecialFormatEscape.LineSeparator :: Nil),
      "%% %% "    -> (SpecialFormatEscape.PercentChar :: Text(" ") :: SpecialFormatEscape.PercentChar :: Text(" ") :: Nil),
      "%%"        -> (SpecialFormatEscape.PercentChar :: Nil),
      "%% a %%"   -> (SpecialFormatEscape.PercentChar :: Text(" a ") :: SpecialFormatEscape.PercentChar :: Nil),
      " %% a %% " -> (Text(" ") ::
        SpecialFormatEscape.PercentChar :: Text(" a ") :: SpecialFormatEscape.PercentChar ::
        Text(" ") :: Nil),
      "%% %% %n %n" -> (SpecialFormatEscape.PercentChar :: Text(" ") ::
        SpecialFormatEscape.PercentChar :: Text(" ") ::
        SpecialFormatEscape.LineSeparator :: Text(" ") ::
        SpecialFormatEscape.LineSeparator :: Nil),
    )
    data.zipWithIndex.foreach { case ((in, out), idx) =>
      val result = parse(in)
      assertEquals(s"error on input with index: $idx\ninput: $in", out, result)
    }
  }

  /**
   * NOTE: invalid specifiers be actually marked as malformed in the editor
   * in [[org.jetbrains.plugins.scala.codeInspection.format.ScalaMalformedFormatStringInspection]]
   */
  def testMixedMalformedSpecifiers(): Unit = {
    assertMatches(parseFull(""""%".format(1)""")) {
      case Injection(_, Some(Specifier(Span(_, 1, 2), "%"))) :: Nil =>
    }
    assertMatches(parseFull(""""%  d".format(1)""")) {
      case Injection(_, Some(Specifier(Span(_, 1, 5), "%  d"))) :: Nil =>
    }
    assertMatches(parseFull(""""% % %".format(1, 2)""")) {
      case Injection(_, Some(Specifier(Span(_, 1, 4), "% %"))) ::
        Text(" ") ::
        Injection(_, Some(Specifier(Span(_, 5, 6), "%"))) :: Nil =>
    }
    assertMatches(parseFull(""""%%%".format(1)""")) {
      case SpecialFormatEscape.PercentChar ::
        Injection(_, Some(Specifier(Span(_, 3, 4), "%"))) :: Nil =>
    }
    assertMatches(parseFull(""""%    aaa".format(1)""")) {
      case Injection(_, Some(Specifier(Span(_, 1, 7), "%    a"))) :: Text("aa") :: Nil =>
    }
    assertMatches(parseFull(""""aaa %".format(1)""")) {
      case Text("aaa ") :: Injection(_, Some(Specifier(Span(_, 5, 6), "%"))) :: Nil =>
    }
    assertMatches(parseFull(""""aaa % ".format(1)""")) {
      case Text("aaa ") :: Injection(_, Some(Specifier(Span(_, 5, 7), "% "))) :: Nil =>
    }
    assertMatches(parseFull(""""% %".format(1)""")) {
      case Injection(_, Some(Specifier(Span(_, 1, 4), "% %"))) :: Nil =>
    }
    assertMatches(parseFull(""""aaa % %".format(1)""")) {
      case Text("aaa ") :: Injection(_, Some(Specifier(Span(_, 5, 8), "% %"))) :: Nil =>
    }
    assertMatches(parseFull(""""% aaa %".format(1, 2)""")) {
      case Injection(_, Some(Specifier(Span(_, 1, 4), "% a"))) :: Text("aa ") ::
        Injection(_, Some(Specifier(Span(_, 7, 8), "%"))) :: Nil =>
    }
    assertMatches(parseFull(""""% 1  % 2".format(1, 2)""")) {
      case Injection(_, Some(Specifier(Span(_, 1, 5), "% 1 "))) :: Text(" ") ::
        Injection(_, Some(Specifier(Span(_, 6, 9), "% 2"))) :: Nil =>
    }
    assertMatches(parseFull(""""%  d".format(1)""")) {
      case Injection(_, Some(Specifier(Span(_, 1, 5), "%  d"))) :: Nil =>
    }
    assertMatches(parseFull(""""%   d".format(1)""")) {
      case Injection(_, Some(Specifier(Span(_, 1, 6), "%   d"))) :: Nil =>
    }
    assertMatches(parseFull(""""%##d".format(1)""")) {
      case Injection(_, Some(Specifier(Span(_, 1, 5), "%##d"))) :: Nil =>
    }

    assertMatches(parseFull(""""%".format()""")) { case UnboundSpecifier(Specifier(Span(_, 1, 2), "%")) :: Nil => }
    assertMatches(parseFull(""""%  d".format()""")){ case UnboundSpecifier(Specifier(Span(_, 1, 5), "%  d")) :: Nil => }
  }

  private def parse(formatString: String, arguments: Int*): List[StringPart] = {
    val stringWithFormatCall = s""""$formatString".format(${arguments.mkString(", ")})"""
    parseFull(stringWithFormatCall)
    //implicit val project: Project = this.getProject
    //val expressions = arguments.map(it => createExpressionFromText(it.toString))
    //val literal = createScalaElementFromText[ScLiteral]("\"" + formatString + "\"")
    //FormattedStringParser.parseFormatCall(literal, expressions).toList
  }

  /** @param stringWithFormatCall example: "%d".format(1) */
  private def parseFull(stringWithFormatCall: String): List[StringPart] =
    parseFullOpt(stringWithFormatCall).getOrElse(fail(s"can't parse parts of literal: ${stringWithFormatCall}").asInstanceOf[Nothing])

  private def parseFullOpt(stringWithFormatCall: String): Option[List[StringPart]] = {
    implicit val project: Project = this.getProject
    val file = createScalaFileFromText(stringWithFormatCall)
    val call = file.getFirstChild.asInstanceOf[ScMethodCall]
    FormattedStringParser.parse(call).map(_.toList)
  }
}
