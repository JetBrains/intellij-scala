package org.jetbrains.plugins.hocon
package lexer

import org.junit.runner.RunWith
import org.junit.runners.AllTests

@RunWith(classOf[AllTests])
class HoconLexerTest extends HoconFileSetTestCase("lexer") {

  def advance(lexer: HoconLexer): HoconLexer = {
    lexer.advance()
    lexer
  }

  override protected def transform(data: Seq[String]): String = {
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

object HoconLexerTest extends TestSuiteCompanion[HoconLexerTest]
