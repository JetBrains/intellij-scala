package org.jetbrains.plugins.scala.lang.scaladoc.reflinks.lexer

import com.intellij.lang.Language
import com.intellij.lexer.Lexer
import org.jetbrains.plugins.scala.lang.lexer.LexerTestBase
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.ScalaDocRefLinkLanguage

import java.nio.file.Path

class ScalaDocRefLinkLexerTest extends LexerTestBase {
  override protected def relativeTestDataPath: Path = Path.of("lexer", "scalaDocRefLinkData")

  override protected def language: Language = ScalaDocRefLinkLanguage.INSTANCE

  override protected def createLexer: Lexer = new ScalaDocRefLinkLexer

  override protected def printTokenRange(tokenStart: Int, tokenEnd: Int, builder: StringBuilder): Unit = {}
}
