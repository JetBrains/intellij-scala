
package org.jetbrains.plugins.scala.lang.scaladoclexer;

import junit.framework.Test;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.lexer.LexerTestBase;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocLexer;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;


@RunWith(AllTests.class)
public class ScaladocLexerTest extends LexerTestBase {

    public ScaladocLexerTest() {
        super(TestUtils.getTestDataPath() + "/lexer/scaladocdata/scaladoc");
    }

    @NotNull
    @Override
    protected ScalaDocLexer createLexer() {
        return new ScalaDocLexer();
    }

    @Override
    protected void printTokenRange(int tokenStart, int tokenEnd,
                                   @NotNull StringBuilder builder) {
    }

    @NotNull
    public static Test suite() {
        return new ScaladocLexerTest();
    }
}

