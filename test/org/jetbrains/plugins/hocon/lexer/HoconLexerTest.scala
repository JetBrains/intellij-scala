package org.jetbrains.plugins.hocon.lexer

import org.jetbrains.plugins.hocon.{HoconFileSetTestCase, TestSuiteCompanion}
import org.junit.runner.RunWith
import org.junit.runners.AllTests

object HoconLexerTest extends TestSuiteCompanion[HoconLexerTest]

@RunWith(classOf[AllTests])
class HoconLexerTest extends HoconFileSetTestCase("lexer") {
  def advance(lexer: HoconLexer) = {
    lexer.advance()
    lexer
  }

  override def transform(data: Seq[String]) = {
    val fileContents = data.head
    val lexer = new HoconLexer

    lexer.start(fileContents)
    val tokenIterator = Iterator.iterate(lexer)(advance)
      .takeWhile(_.getTokenType != null)
      .map(l => (l.getTokenType, fileContents.substring(l.getTokenStart, l.getTokenEnd)))

    tokenIterator.map {
      case (token, str) => s"$token {$str}"
    }.mkString("\n")
  }
}
