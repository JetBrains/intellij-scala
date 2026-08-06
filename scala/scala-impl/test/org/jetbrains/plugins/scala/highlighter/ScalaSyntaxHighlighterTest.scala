package org.jetbrains.plugins.scala.highlighter

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.scala.ScalaColorSchemeEditorHighlightingFixture
import org.jetbrains.plugins.scala.ScalaColorSchemeEditorHighlightingFixture.ExpectedHighlight
import org.junit.Assert.assertTrue

import scala.collection.mutable.ArrayBuffer

class ScalaSyntaxHighlighterTest extends BasePlatformTestCase {

  def testScalaSyntaxHighlighterObjectFieldsAreSuccessfullyInitialized(): Unit = {
    //noinspection ScalaUnusedExpression
    ScalaSyntaxHighlighter.toString
  }

  def testColonIsHighlightedInScalaCode(): Unit = {
    assertTokenHighlights("val value: Int = 42", ExpectedHighlight(":", DefaultHighlighter.COLON))
  }

  def testBadCharacterIsHighlightedInScalaCode(): Unit = {
    val badCharacter = "\u0000"
    assertTokenHighlights(s"val value = $badCharacter", ExpectedHighlight(badCharacter, DefaultHighlighter.BAD_CHARACTER))
  }

  def testLiteralsCommentsAndPunctuationAreHighlightedInScalaCode(): Unit = {
    val source =
      """// line comment
        |/* block comment */
        |val number = 42
        |val string = "plain text"
        |val validEscape = "\n"
        |val invalidEscape = "\g"
        |val punctuation = (List[Int]{ 1 }, 2); punctuation.toString
        |val function: Int => Int = value => value
        |""".stripMargin

    assertTokenHighlights(
      source,
      ExpectedHighlight("// line comment", DefaultHighlighter.LINE_COMMENT),
      ExpectedHighlight("/* block comment */", DefaultHighlighter.BLOCK_COMMENT),
      ExpectedHighlight("val", DefaultHighlighter.KEYWORD),
      ExpectedHighlight("42", DefaultHighlighter.NUMBER),
      ExpectedHighlight("\"plain text\"", DefaultHighlighter.STRING),
      ExpectedHighlight("\\n", DefaultHighlighter.VALID_STRING_ESCAPE),
      ExpectedHighlight("\\g", DefaultHighlighter.INVALID_STRING_ESCAPE),
      ExpectedHighlight("{", DefaultHighlighter.BRACES),
      ExpectedHighlight("[", DefaultHighlighter.BRACKETS),
      ExpectedHighlight("(", DefaultHighlighter.PARENTHESES),
      ExpectedHighlight(":", DefaultHighlighter.COLON),
      ExpectedHighlight("=", DefaultHighlighter.ASSIGN),
      ExpectedHighlight("=>", DefaultHighlighter.ARROW),
      ExpectedHighlight(";", DefaultHighlighter.SEMICOLON),
      ExpectedHighlight(".", DefaultHighlighter.DOT),
      ExpectedHighlight(",", DefaultHighlighter.COMMA),
    )
  }

  def testScalaDocXmlInterpolationAndDirectivesAreHighlightedInScalaCode(): Unit = {
    val source =
      """//> using dep "org.example::library:1.0"
        |/**
        | * documented text
        | * <code>html text</code>
        | * &#94;
        | * ''wiki text''
        | * @param parameterName description
        | */
        |val injection = 1
        |val interpolated = s"value: $injection"
        |val xml = <element attribute="value">xml data<!-- xml comment --></element>
        |""".stripMargin

    assertTokenHighlights(
      source,
      ExpectedHighlight("//>", DefaultHighlighter.SCALA_DIRECTIVE_PREFIX),
      ExpectedHighlight("using", DefaultHighlighter.SCALA_DIRECTIVE_COMMAND),
      ExpectedHighlight("dep", DefaultHighlighter.SCALA_DIRECTIVE_KEY),
      ExpectedHighlight("\"org.example::library:1.0\"", DefaultHighlighter.SCALA_DIRECTIVE_VALUE),
      ExpectedHighlight("documented", DefaultHighlighter.DOC_COMMENT),
      ExpectedHighlight("<", DefaultHighlighter.SCALA_DOC_HTML_TAG),
      ExpectedHighlight("&#94;", DefaultHighlighter.SCALA_DOC_HTML_ESCAPE),
      ExpectedHighlight("''", DefaultHighlighter.SCALA_DOC_WIKI_SYNTAX),
      ExpectedHighlight("@param", DefaultHighlighter.SCALA_DOC_TAG),
      ExpectedHighlight("parameterName", DefaultHighlighter.SCALA_DOC_TAG_PARAM_VALUE),
      ExpectedHighlight("$", DefaultHighlighter.INTERPOLATED_STRING_INJECTION),
      ExpectedHighlight("<", DefaultHighlighter.XML_TAG, occurrence = 2),
      ExpectedHighlight("element", DefaultHighlighter.XML_TAG_NAME),
      ExpectedHighlight("attribute", DefaultHighlighter.XML_ATTRIBUTE_NAME),
      ExpectedHighlight("value", DefaultHighlighter.XML_ATTRIBUTE_VALUE, occurrence = 1),
      ExpectedHighlight("xml", DefaultHighlighter.XML_TAG_DATA, occurrence = 1),
      ExpectedHighlight("<!--", DefaultHighlighter.XML_COMMENT),
    )
  }

  private def assertTokenHighlights(source: String, expectedHighlights: ExpectedHighlight*): Unit = {
    val tokens = tokenHighlights(source)
    expectedHighlights.foreach { expected =>
      val startOffset = ScalaColorSchemeEditorHighlightingFixture.findOccurrence(source, expected)
      assertTrue(
        s"The source must contain occurrence ${expected.occurrence} of '${expected.text}'",
        startOffset >= 0
      )

      val endOffset = startOffset + expected.text.length
      val actualTokens = tokens.map(token => s"'${token.text}' (${token.tokenType})").mkString(", ")
      val found = tokens.exists { token =>
        token.attributes.contains(expected.key) &&
          token.startOffset <= startOffset &&
          endOffset <= token.endOffset
      }

      assertTrue(
        s"No token for '${expected.text}' has ${expected.key}. Actual tokens: $actualTokens",
        found
      )
    }
  }

  private def tokenHighlights(source: String): Seq[TokenHighlight] = {
    val scalaFile = myFixture.configureByText("Example.scala", source)
    val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(
      scalaFile.getLanguage,
      getProject,
      scalaFile.getVirtualFile
    )
    val lexer = highlighter.getHighlightingLexer
    lexer.start(scalaFile.getText)

    val tokens = ArrayBuffer.empty[TokenHighlight]
    while (lexer.getTokenType != null) {
      tokens += TokenHighlight(
        lexer.getTokenText,
        lexer.getTokenType,
        highlighter.getTokenHighlights(lexer.getTokenType).toSeq,
        lexer.getTokenStart,
        lexer.getTokenEnd
      )
      lexer.advance()
    }
    tokens.toSeq
  }

  private case class TokenHighlight(
    text: String,
    tokenType: IElementType,
    attributes: Seq[TextAttributesKey],
    startOffset: Int,
    endOffset: Int
  )
}
