package org.jetbrains.plugins.scala.lang.scaladoclexer;

import com.intellij.psi.tree.IElementType;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.Console;
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocLexer;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;

/**
 * User: Dmitry Naidanov
 * Date: 11/9/11
 */
public class ScaladocLexerTest extends BaseScalaFileSetTestCase {
  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/scala/lang/lexer/scaladocdata/scaladoc";

  public ScaladocLexerTest() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        DATA_PATH
    );
  }
  
  @Override
  public String transform(String testName, String[] data) throws Exception {
    ScalaDocLexer lexer = new ScalaDocLexer();
    lexer.start(data[0]);

    StringBuilder buffer = new StringBuilder();

    IElementType type;
    while ((type = lexer.getTokenType()) != null) {
      CharSequence s = lexer.getBufferSequence();
      s = s.subSequence(lexer.getTokenStart(), lexer.getTokenEnd());
      buffer.append(type.toString()).append(" {").append(s).append("}");
      lexer.advance();
      if (lexer.getTokenType() != null) {
        buffer.append("\n");
      }
    }

    Console.println("------------------------ " + testName + " ------------------------");
    Console.println(buffer.toString());
    Console.println("");

    return buffer.toString();
  }

  public static Test suite() {
    return new ScaladocLexerTest();
  }
}
