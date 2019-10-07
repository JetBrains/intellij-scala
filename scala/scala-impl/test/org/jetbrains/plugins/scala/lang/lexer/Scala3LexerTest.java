package org.jetbrains.plugins.scala.lang.lexer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.Scala3Language;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

@RunWith(AllTests.class)
public class Scala3LexerTest extends ScalaLexerTestBase {

    Scala3LexerTest() {
        super("/lexer/data3");
    }

    @NotNull
    @Override
    protected Scala3Language getLanguage() {
        return Scala3Language.INSTANCE;
    }

    @NotNull
    public static Scala3LexerTest suite() {
        return new Scala3LexerTest();
    }
}
