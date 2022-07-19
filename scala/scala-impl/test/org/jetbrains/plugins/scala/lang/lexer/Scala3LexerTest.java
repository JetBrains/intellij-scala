package org.jetbrains.plugins.scala.lang.lexer;

import junit.framework.Test;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.Scala3Language;

public class Scala3LexerTest extends TestCase {
    @NotNull
    public static Test suite() {
        return new ScalaLexerTestBase("/lexer/data3") {
            @NotNull
            @Override
            protected Scala3Language getLanguage() {
                return Scala3Language.INSTANCE;
            }
        };
    }
}
