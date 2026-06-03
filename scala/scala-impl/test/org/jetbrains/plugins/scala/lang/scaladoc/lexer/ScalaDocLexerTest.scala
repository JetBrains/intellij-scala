package org.jetbrains.plugins.scala.lang.scaladoc.lexer

import com.intellij.lang.Language
import org.jetbrains.plugins.scala.lang.lexer.LexerTestBase
import org.jetbrains.plugins.scalaDoc.ScalaDocLanguage

import java.nio.file.Path

class ScalaDocLexerTest extends LexerTestBase {
  override protected def relativeTestDataPath: Path = Path.of("lexer", "scalaDocData")

  override protected def language: Language = ScalaDocLanguage.INSTANCE

  override protected def printTokenRange(tokenStart: Int, tokenEnd: Int, builder: StringBuilder): Unit = {}
}
