package intellijhocon.lexer

import org.scalatest.FunSuite
import com.intellij.psi.tree.IElementType
import scala.collection.mutable.ListBuffer

class HoconLexerTest extends FunSuite {

  import HoconTokenType._

  private def lex(str: String): List[(IElementType, String)] = {
    val lexer = new HoconLexer
    lexer.start(str)
    val lb = new ListBuffer[(IElementType, String)]
    while (lexer.getTokenType != null) {
      lb += ((lexer.getTokenType, lexer.getTokenText))
      lexer.advance()
    }
    lb.result()
  }

  test("basic quoted string test") {
    assert(List(
      (QuotedString, "\"something\"")
    ) === lex("\"something\""))
  }

  test("quoted string with escaping test") {
    assert(List(
      (QuotedString, "\"some\\nthing\"")
    ) === lex("\"some\\nthing\""))
  }

  test("quoted string with double quote escaping test") {
    assert(List(
      (QuotedString, "\"something\\\"\""),
      (UnquotedChars, "somethingmore")
    ) === lex("\"something\\\"\"somethingmore"))
  }

  test("quoted string ending on newline test") {
    assert(List(
      (QuotedString, "\"something"),
      (LineBreakingWhitespace, "\n"),
      (UnquotedChars, "more")
    ) === lex("\"something\nmore"))
  }

}
