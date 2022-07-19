package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import junit.framework.Test;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

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
public class LexerStateTest extends TestCase {
    @NotNull
    public static Test suite() {
        return new LexerTestBase("/lexer/state") {
            @Override
            protected void onToken(@NotNull Lexer lexer,
                                   @NotNull IElementType tokenType,
                                   @NotNull StringBuilder builder) {
                onEof(lexer, builder);
            }

            @Override
            protected void onEof(@NotNull Lexer lexer, @NotNull StringBuilder builder) {
                builder.append(lexer.getState()).append('\n');
            }
        };
    }
}
