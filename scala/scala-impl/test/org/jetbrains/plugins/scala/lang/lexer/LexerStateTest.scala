package org.jetbrains.plugins.scala.lang.lexer

import junit.framework.TestCase
import org.junit.Assert._

/**
 * @author Pavel Fatin
 */

// An ability of lexer to cycle through its initial state is critically important
// for incremental highlighting (see LexerEditorHighlighter).
// However it's very easy to break this functionality and nobody would notice,
// so it's better to test state transitions explicitly.
class LexerStateTest extends TestCase {
  // In some places (like LexerEditorHighlighter) initial state
  // is assumed to be equal to a state after starting analysis of an empty string.
  // However in other places (like Lexer itself) there's an assumption that initial state 0 is valid.
  // So it's better to ensure that 0 value is used as initial state.
  def testInitialState() {
    assertStates("", 0)
  }

  def testPlainContent() {
    assertStates("foo;", 0, 0, 0)

    // Why do we have this non-initial "common" state from the underlying JFlex lexer?
    assertStates("class C {\n}", 0, 0, 239, 0, 0, 0, 0, 0)
  }

  // Ensure that core lexer state is propagated
  def testCoreLexerState() {
    assertStates("s\"foo\";", 0, 239, 239, 0, 0)
  }

  def testXmlContent() {
    assertStates("<foo/>;", 0, 239, 239, 0, 0)
  }

  private def assertStates(s: String, states: Int*) {
    assertEquals(states.toList, statesIn(s).toList)
  }

  private def statesIn(s: String): Seq[Int] = {
    val lexer = new ScalaLexer()
    lexer.start(s)
    var result = Seq.empty[Int]
    while (lexer.getTokenType != null) {
      result :+= lexer.getState
      lexer.advance()
    }
    result :+= lexer.getState
    result
  }
}
