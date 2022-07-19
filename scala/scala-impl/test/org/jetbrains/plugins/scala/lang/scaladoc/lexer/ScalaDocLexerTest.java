package org.jetbrains.plugins.scala.lang.scaladoc.lexer;

import com.intellij.lang.Language;
import junit.framework.Test;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.lexer.LexerTestBase;
import org.jetbrains.plugins.scalaDoc.ScalaDocLanguage;

public class ScalaDocLexerTest extends TestCase {
    @NotNull
    public static Test suite() {
        return new LexerTestBase("/lexer/scalaDocData") {
            @NotNull
            @Override
            protected Language getLanguage() {
                return ScalaDocLanguage.INSTANCE;
            }

            @Override
            protected void printTokenRange(int tokenStart, int tokenEnd, @NotNull StringBuilder builder) {
            }
        };
    }
}

