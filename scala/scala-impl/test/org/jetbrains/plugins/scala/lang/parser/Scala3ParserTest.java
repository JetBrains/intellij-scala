package org.jetbrains.plugins.scala.lang.parser;

import com.intellij.lang.Language;
import junit.framework.Test;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.Scala3Language;
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

@RunWith(AllTests.class)
public class Scala3ParserTest extends ScalaFileSetTestCase {

    Scala3ParserTest() {
        super("/parser/data3");
    }

    @NotNull
    @Override
    protected Language getLanguage() {
        return Scala3Language.INSTANCE;
    }

    @NotNull
    public static Test suite() {
        return new Scala3ParserTest();
    }
}
