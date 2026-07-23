package org.jetbrains.plugins.scala.highlighter

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.tCOLON
import org.junit.Assert.assertEquals

class ScalaSyntaxHighlighterTest extends BasePlatformTestCase {

  def testScalaSyntaxHighlighterObjectFieldsAreSuccessfullyInitialized(): Unit = {
    //noinspection ScalaUnusedExpression
    ScalaSyntaxHighlighter.toString
  }

  def testColonIsHighlightedInScalaCode(): Unit = {
    val scalaFile = myFixture.configureByText("Example.scala", "val value: Int = 42")
    val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(
      scalaFile.getLanguage,
      getProject,
      scalaFile.getVirtualFile
    )
    val lexer = highlighter.getHighlightingLexer
    lexer.start(scalaFile.getText)

    var colonAttributes: Seq[TextAttributesKey] = Seq.empty
    while (lexer.getTokenType != null) {
      if (lexer.getTokenType == tCOLON)
        colonAttributes = highlighter.getTokenHighlights(lexer.getTokenType).toSeq
      lexer.advance()
    }

    assertEquals(Seq(DefaultHighlighter.COLON), colonAttributes)
  }
}