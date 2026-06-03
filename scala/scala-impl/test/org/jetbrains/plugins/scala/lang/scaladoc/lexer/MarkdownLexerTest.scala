package org.jetbrains.plugins.scala.lang.scaladoc.lexer

import com.intellij.lang.{Language, LanguageParserDefinitions}
import com.intellij.lexer.Lexer
import org.jetbrains.plugins.scala.lang.lexer.LexerTestBase
import org.jetbrains.plugins.scalaDoc.ScalaDocLanguage
import org.jetbrains.plugins.scalaDoc.lang.parser.ScalaDocParserDefinition

import java.nio.file.Path

class MarkdownLexerTest extends LexerTestBase {
  override protected def relativeTestDataPath: Path = Path.of("lexer", "markdown")

  override protected def language: Language = ScalaDocLanguage.INSTANCE

  override protected def createLexer: Lexer =
    LanguageParserDefinitions.INSTANCE.forLanguage(language)
      .asInstanceOf[ScalaDocParserDefinition]
      .createLexerWithFlavour(project, isMarkdown = true)

  override protected def printTokenRange(tokenStart: Int, tokenEnd: Int, builder: StringBuilder): Unit = {}
}
