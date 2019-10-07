package org.jetbrains.plugins.scala.lang.lexer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.PerfCycleTests;
import org.junit.experimental.categories.Category;

@Category({PerfCycleTests.class})
public class FailedLexerTest extends ScalaLexerTestBase {

    FailedLexerTest() {
        super("/lexer/failed");
    }

    @Override
    protected boolean shouldPass() {
        return false;
    }

    @NotNull
    public static FailedLexerTest suite() {
        return new FailedLexerTest();
    }
}
