package org.jetbrains.plugins.scala.lang.lexer

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.Scala3Language
import org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighterFactory
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers

/**
 * Also see [[org.jetbrains.plugins.scala.lang.lexer.ScalaSyntaxHighlighterProjectModelChangeIntegrationTest]]
 * for a more integrational test for ensuring that scala highlighter is recreated on any project model change
 */
class LexerCreationTest extends LightJavaCodeInsightFixtureTestCase with AssertionMatchers {
  def testTwoScalaHighlightingLexersDoNotInterfere(): Unit = {
    val lang = Scala3Language.INSTANCE
    val file = this.createLightFile("test.scala", lang, "package test")
    val vfile = file.getViewProvider.getVirtualFile
    assert(vfile != null)

    val highlighter = ScalaSyntaxHighlighterFactory.createScalaSyntaxHighlighter(getProject, vfile, lang)

    val lexer1 = highlighter.getHighlightingLexer
    val lexer2 = highlighter.getHighlightingLexer

    lexer1.start(file.getText)
    lexer2.start(file.getText)

    lexer1.getTokenType shouldBe ScalaTokenTypes.kPACKAGE
    lexer2.getTokenType shouldBe ScalaTokenTypes.kPACKAGE

    lexer1.advance()

    lexer1.getTokenType shouldBe ScalaTokenTypes.tWHITE_SPACE_IN_LINE
    // lexer1 and lexer2 should not share a state even though they come from the same highlighter
    lexer2.getTokenType shouldBe ScalaTokenTypes.kPACKAGE
  }
}
