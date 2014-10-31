
package org.jetbrains.plugins.scala.lang.scaladoclexer;

import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.lang.lexer.LexerTestBase;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocLexer;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;


@RunWith(AllTests.class)
public class ScaladocLexerTest extends LexerTestBase {
  @NonNls
  private static final String DATA_PATH = "/lexer/scaladocdata/scaladoc";

  public ScaladocLexerTest() {
    super(TestUtils.getTestDataPath() + DATA_PATH, new ScalaDocLexer());
  }

  public static Test suite() {
    return new ScaladocLexerTest();
  }
}

