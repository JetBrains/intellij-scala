package org.jetbrains.plugins.scala.lang.lexer

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lexer.Lexer
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.base.NoSdkFileSetTestBase

abstract class LexerTestBase extends NoSdkFileSetTestBase {

  protected def createLexer: Lexer =
    LanguageParserDefinitions.INSTANCE.forLanguage(language).createLexer(project)

  protected def onToken(lexer: Lexer, tokenType: IElementType, builder: StringBuilder): Unit = {
    builder.append(tokenType.toString)
    printTokenRange(lexer.getTokenStart, lexer.getTokenEnd, builder)
    printTokenText(lexer.getTokenText, builder)
    builder.append('\n')
  }

  protected def onEof(lexer: Lexer, builder: StringBuilder): Unit = {}

  protected def printTokenRange(tokenStart: Int, tokenEnd: Int, builder: StringBuilder): Unit =
    builder.append(':').append(' ').append('[')
      .append(tokenStart)
      .append(',').append(' ')
      .append(tokenEnd)
      .append(']').append(',')


  override protected def transform(testName: String, fileText: String): String = {
    val lexer = createLexer
    lexer.start(fileText)

    val builder = new StringBuilder()

    var tokenType = lexer.getTokenType
    while (tokenType != null) {
      onToken(lexer, tokenType, builder)

      lexer.advance()
      tokenType = lexer.getTokenType
    }

    onEof(lexer, builder)
    builder.toString
  }

  private def printTokenText(tokenText: String, builder: StringBuilder): Unit =
    builder.append(' ').append('{')
      .append(tokenText)
      .append('}')
}
