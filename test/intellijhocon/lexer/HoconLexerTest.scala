package intellijhocon
package lexer

import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase
import org.jetbrains.plugins.scala.util.TestUtils

class HoconLexerTest extends BaseScalaFileSetTestCase(TestUtils.getTestDataPath + "/hocon/lexer/data") {
  def advance(lexer: HoconLexer) = {
    lexer.advance()
    lexer
  }

  override def transform(testName: String, data: Array[String]) = {
    val fileContents = data(0)
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
