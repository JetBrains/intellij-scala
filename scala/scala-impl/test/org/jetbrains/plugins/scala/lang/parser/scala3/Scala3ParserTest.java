package org.jetbrains.plugins.scala.lang.parser.scala3;

import com.intellij.lang.Language;
import junit.framework.Test;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.Scala3Language;
import org.jetbrains.plugins.scala.lang.parser.ScalaFileSetParserTestCase;

public class Scala3ParserTest extends TestCase {
    @NotNull
    public static Test suite() {
        // TODO: test: change from data3 to data and check all failures, are they expected?
        //  if yes, add a dedicated test for Scala3 with a different expected result
        return new ScalaFileSetParserTestCase("/parser/data3") {
            @NotNull
            @Override
            protected Language getLanguage() {
                return Scala3Language.INSTANCE;
            }
        };
    }
}
