
package org.jetbrains.plugins.scala.lang.scaladoclexer;

import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.lang.lexer.LexerTestBase;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocLexer;


public class ScaladocLexerTest extends LexerTestBase {
  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/scala/lang/lexer/scaladocdata/scaladoc";

  public ScaladocLexerTest() {
    super(DATA_PATH, new ScalaDocLexer());
  }

  public static Test suite() {
    return new ScaladocLexerTest();
  }
}

