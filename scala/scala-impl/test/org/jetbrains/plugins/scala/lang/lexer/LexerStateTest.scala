package org.jetbrains.plugins.scala.lang.lexer

import com.intellij.lexer.Lexer
import com.intellij.psi.tree.IElementType

import java.nio.file.Path

/**
 * An ability of lexer to cycle through its initial state is critically important
 * for incremental highlighting (see LexerEditorHighlighter).
 * <p>
 * However it's very easy to break this functionality and nobody would notice,
 * so it's better to test state transitions explicitly.
 * <p>
 * In some places (like LexerEditorHighlighter) initial state
 * is assumed to be equal to a state after starting analysis of an empty string.
 * However in other places (like Lexer itself) there's an assumption that initial state 0 is valid.
 * So it's better to ensure that 0 value is used as initial state.
 */
class LexerStateTest extends LexerTestBase {
  override protected def relativeTestDataPath: Path = Path.of("lexer", "state")

  override protected def onToken(lexer: Lexer, tokenType: IElementType, builder: StringBuilder): Unit = {
    onEof(lexer, builder)
  }

  override protected def onEof(lexer: Lexer, builder: StringBuilder): Unit = {
    builder.append(lexer.getState).append('\n')
  }
}
