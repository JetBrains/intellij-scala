package org.jetbrains.plugins.scala.lang.lexer;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.LanguageTests;
import org.junit.experimental.categories.Category;

@Category({LanguageTests.class})
public abstract class ScalaLexerTestBase extends LexerTestBase {

    ScalaLexerTestBase(@NotNull @NonNls String dataPath) {
        super(dataPath);
    }

    @Override
    protected void printTokenRange(int tokenStart, int tokenEnd,
                                   @NotNull StringBuilder builder) {
    }
}
