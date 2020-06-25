package org.jetbrains.plugins.scala.lang.scaladoc.lexer;

import com.intellij.lang.Language;
import junit.framework.Test;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.lexer.LexerTestBase;
import org.jetbrains.plugins.scalaDoc.ScalaDocLanguage;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

@RunWith(AllTests.class)
public class ScalaDocLexerTest extends LexerTestBase {

    public ScalaDocLexerTest() {
        super("/lexer/scalaDocData");
    }

    @NotNull
    @Override
    protected Language getLanguage() {
        return ScalaDocLanguage.INSTANCE;
    }

    @Override
    protected void printTokenRange(int tokenStart, int tokenEnd,
                                   @NotNull StringBuilder builder) {
    }

    @NotNull
    public static Test suite() {
        return new ScalaDocLexerTest();
    }
}

