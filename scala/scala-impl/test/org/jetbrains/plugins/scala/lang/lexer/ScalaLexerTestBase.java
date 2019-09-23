package org.jetbrains.plugins.scala.lang.lexer;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ScalaLexerTestBase extends LexerTestBase {

    ScalaLexerTestBase(@NotNull @NonNls String dataPath) {
        super(dataPath);
    }

    @Override
    protected void printTokenRange(int tokenStart, int tokenEnd,
                                   @NotNull StringBuilder builder) {
    }
}
