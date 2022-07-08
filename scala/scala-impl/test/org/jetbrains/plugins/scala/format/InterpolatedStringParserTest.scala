package org.jetbrains.plugins.scala
package format

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions.ElementText
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions._
import org.junit.Assert.assertEquals

class InterpolatedStringParserTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testEmpty(): Unit = {
    assertMatches(parseF("")) { case Nil => }
    assertMatches(parseS("")) { case Nil => }
    assertMatches(parseRaw("")) { case Nil => }
  }

  def testText(): Unit = {
    assertMatches(parseF("foo")) { case Text("foo") :: Nil => }
    assertMatches(parseS("foo")) { case Text("foo") :: Nil => }
    assertMatches(parseRaw("foo")) { case Text("foo") :: Nil => }
  }

  def testEscapeChar(): Unit = {
    assertMatches(parseF("\\n \\t \\\\")) { case Text("\n \t \\") :: Nil => }
    assertMatches(parseS("\\n \\t \\\\")) { case Text("\n \t \\") :: Nil => }
    assertMatches(parseRaw("\\n \\t \\ \\\\")) { case Text("\\n \\t \\ \\\\") :: Nil => }
  }

  def testEscapeCharInMultilineString(): Unit = {
    assertMatches(parseF("\n \\n \\\\", multiline = true)) { case Text("\n \n \\") :: Nil => }
    assertMatches(parseS("\n \\n \\\\", multiline = true)) { case Text("\n \n \\") :: Nil => }
    assertMatches(parseRaw("\n \\n \\\\", multiline = true)) { case Text("\n \\n \\\\") :: Nil => }
  }

  def testDollarEscapeChar(): Unit = {
    assertMatches(parseF("$$")) { case Text("$") :: Nil => }
    assertMatches(parseS("$$")) { case Text("$") :: Nil => }
    assertMatches(parseRaw("$$")) { case Text("$") :: Nil => }
  }

  def testExpression(): Unit = {
    assertMatches(parseF("$foo")) { case Injection(ElementText("foo"), None) :: Nil => }
    assertMatches(parseS("$foo")) { case Injection(ElementText("foo"), None) :: Nil => }
    assertMatches(parseRaw("$foo")) { case Injection(ElementText("foo"), None) :: Nil => }
  }

  def testBlockExpression(): Unit = {
    assertMatches(parseF("${foo.bar}")) { case Injection(ElementText("foo.bar"), None) :: Nil => }
    assertMatches(parseS("${foo.bar}")) { case Injection(ElementText("foo.bar"), None) :: Nil => }
    assertMatches(parseRaw("${foo.bar}")) { case Injection(ElementText("foo.bar"), None) :: Nil => }
  }

  def testComplexBlockExpression(): Unit = {
    assertMatches(parseF("${null; foo}")) { case Injection(ElementText("{null; foo}"), None) :: Nil => }
    assertMatches(parseS("${null; foo}")) { case Injection(ElementText("{null; foo}"), None) :: Nil => }
    assertMatches(parseRaw("${null; foo}")) { case Injection(ElementText("{null; foo}"), None) :: Nil => }
  }

  def testFormattedExpression(): Unit =
    assertMatches(parseF("$foo%d")) { case Injection(ElementText("foo"), Some(Specifier(Span(_, 0, 2), "%d"))) :: Nil => }

  def testExpressionWithSeparatedFormatter(): Unit = {
    assertMatches(parseF("$foo %d")) { case Injection(ElementText("foo"), None) :: Text(" %d") :: Nil => }
    assertMatches(parseF("$foo %d%d")) { case Injection(ElementText("foo"), None) :: Text(" %d%d") :: Nil => }
  }

  def testExpressionWithSpecialFormatEscape(): Unit = {
    assertMatches(parseF("$foo %%")) {
      case Injection(ElementText("foo"), None) :: Text(" ") ::
        SpecialFormatEscape.PercentChar :: Nil =>
    }
    assertMatches(parseF("$foo %%%%")) {
      case Injection(ElementText("foo"), None) :: Text(" ") ::
        SpecialFormatEscape.PercentChar ::
        SpecialFormatEscape.PercentChar :: Nil =>
    }
    assertMatches(parseF("$foo %% %%")) {
      case Injection(ElementText("foo"), None) :: Text(" ") ::
        SpecialFormatEscape.PercentChar :: Text(" ") ::
        SpecialFormatEscape.PercentChar :: Nil =>
    }
    assertMatches(parseF("$foo %% $foo %%")) {
      case Injection(ElementText("foo"), None) :: Text(" ") ::
        SpecialFormatEscape.PercentChar :: Text(" ") ::
        Injection(ElementText("foo"), None) :: Text(" ") ::
        SpecialFormatEscape.PercentChar :: Nil =>
    }
    assertMatches(parseF("$foo %n")) {
      case Injection(ElementText("foo"), None) :: Text(" ") ::
        SpecialFormatEscape.LineSeparator :: Nil =>
    }
  }

  def testExpressionWithSpecialFormatEscape_1(): Unit = {
    import SpecialFormatEscape._

    val data: Seq[(String, Seq[StringPart])] = Seq(
      ""            -> Seq(),
      "text"        -> Seq(Text("text")),
      " %%"         -> Seq(Text(" "), PercentChar),
      " %%%%"       -> Seq(Text(" "), PercentChar, PercentChar),
      " %%%% "       -> Seq(Text(" "), PercentChar, PercentChar, Text(" ")),
      " %% %%"      -> Seq(Text(" "), PercentChar, Text(" "), PercentChar),
      " %% %% "     -> Seq(Text(" "), PercentChar, Text(" "), PercentChar, Text(" ")),
      "%% "         -> Seq(PercentChar, Text(" ")),
      "%% %%"       -> Seq(PercentChar, Text(" "), PercentChar),
      "%% %% "      -> Seq(PercentChar, Text(" "), PercentChar, Text(" ")),
      "%%"          -> Seq(PercentChar),
      "%% %% %n %n" -> Seq(PercentChar, Text(" "), PercentChar, Text(" "), LineSeparator, Text(" "), LineSeparator),

      // wrong/incomplete escapes
      // TODO: handle more properly wrong single %, probably special StringPart type required
      " %"   -> Seq(Text(" %")),
      " %%%" -> Seq(Text(" "), PercentChar, Text("%")),
      " %%%" -> Seq(Text(" "), PercentChar, Text("%")),
      "% "   -> Seq(Text("% "))
    )

    data.foreach { case (input, expected) =>
      val actual = parseF(input)
      assertEquals(s"wrong result for input:\n$input", expected, actual)
    }
  }

  def testFormattedBlockExpression(): Unit =
    assertMatches(parseF("${foo}%d")) {
      case Injection(ElementText("foo"), Some(Specifier(Span(_, 0, 2), "%d"))) :: Nil =>
    }

  def testFormattedComplexBlockExpression(): Unit =
    assertMatches(parseF("${null; foo}%d")) {
      case Injection(ElementText("{null; foo}"), Some(Specifier(Span(_, 0, 2), "%d"))) :: Nil =>
    }

  def testMixed(): Unit = {
    assertMatches(parseF("foo $exp ${it.name}%2d bar")) {
      case Text("foo ") ::
              Injection(ElementText("exp"), None) ::
              Text(" ") ::
              Injection(ElementText("it.name"), Some(Specifier(Span(_, 0, 3), "%2d"))) ::
              Text(" bar") ::
              Nil =>
    }
  }

  def testUnformattedWithSpecifiersLike(): Unit =
    assertMatches(parseS("$foo%d")) {
      case Injection(ElementText("foo"), None) :: Text("%d") :: Nil =>
    }

  def testMultilineFormatted(): Unit =
    assertMatches(parseF("$foo%d $foo1%% $foo2%n", multiline = true)) {
      case Injection(ElementText("foo"), Some(Specifier(Span(_, 0, 2), "%d"))) :: Text(" ") ::
        Injection(ElementText("foo1"), None) :: SpecialFormatEscape.PercentChar :: Text(" ") ::
        Injection(ElementText("foo2"), None) :: SpecialFormatEscape.LineSeparator :: Nil =>
    }

  def testMultilineNotFormatted(): Unit =
    assertMatches(parseS("$foo%d $foo1%% $foo2%n", multiline = true)) {
      case Injection(ElementText("foo"), None) :: Text("%d ") ::
        Injection(ElementText("foo1"), None) :: Text("%% ") ::
        Injection(ElementText("foo2"), None) :: Text("%n") :: Nil =>
    }

  private def parseF(content: String, multiline: Boolean = false): List[StringPart] =
    parse(content, ScInterpolatedStringLiteral.Format, multiline)
  private def parseS(content: String, multiline: Boolean = false): List[StringPart] =
    parse(content, ScInterpolatedStringLiteral.Standard, multiline)
  private def parseRaw(content: String, multiline: Boolean = false): List[StringPart] =
    parse(content, ScInterpolatedStringLiteral.Raw, multiline)

  private def parse(content: String, kind: ScInterpolatedStringLiteral.Kind, multiline: Boolean = false): List[StringPart] = {
    val element = literal(content, kind, multiline)
    InterpolatedStringParser.parse(element).get.toList
  }

  private def literal(s: String, kind: ScInterpolatedStringLiteral.Kind, multiline: Boolean): ScInterpolatedStringLiteral = {
    val text = {
      // TODO: move to method of kind, but how to handle "Pattern"?
      val prefix = kind match {
        case ScInterpolatedStringLiteral.Standard => "s"
        case ScInterpolatedStringLiteral.Format   => "f"
        case ScInterpolatedStringLiteral.Raw      => "raw"
        case ScInterpolatedStringLiteral.Pattern  => ""
      }
      val quote = if (multiline) "\"\"\"" else "\""
      prefix + quote + s + quote
    }
    val file = createLightFile(ScalaFileType.INSTANCE, text).asInstanceOf[ScalaFile]
    file.getFirstChild.asInstanceOf[ScInterpolatedStringLiteral]
  }
}
