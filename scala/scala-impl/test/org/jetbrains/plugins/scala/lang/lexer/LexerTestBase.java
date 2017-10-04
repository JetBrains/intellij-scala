package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.lexer.Lexer;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;

/**
 * User: Dmitry Naidanov
 * Date: 11/21/11
 */
abstract public class LexerTestBase extends BaseScalaFileSetTestCase {
  protected Lexer lexer;
  
  public LexerTestBase(String dataPath, Lexer lexer) {
    super(System.getProperty("path") != null ? System.getProperty("path") : dataPath);
    this.lexer = lexer;
  }

  
  @Override
  public String transform(String testName, String[] data) throws Exception {
    String fileText = data[0];

    lexer.start(fileText);

    StringBuilder buffer = new StringBuilder();

    while (lexer.getTokenType() != null) {
      buffer.append(prettyPrintToken());
      lexer.advance();
      if (lexer.getTokenType() != null) {
        buffer.append("\n");
      }
    }

    return buffer.toString();
  }

  protected String prettyPrintToken() {
    if (lexer.getTokenType() == null) return "null";

    CharSequence s = lexer.getBufferSequence();
    s = s.subSequence(lexer.getTokenStart(), lexer.getTokenEnd());
    return lexer.getTokenType().toString() + " {" + s + "}";
  }
}