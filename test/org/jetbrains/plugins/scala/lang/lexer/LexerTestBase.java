package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.scala.Console;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;

/**
 * User: Dmitry Naidanov
 * Date: 11/21/11
 */
abstract public class LexerTestBase extends BaseScalaFileSetTestCase {
  private final Lexer lexer;
  
  public LexerTestBase(String dataPath, Lexer lexer) {
    super(System.getProperty("path") != null ? System.getProperty("path") : dataPath);
    this.lexer = lexer;
  }

  
  @Override
  public String transform(String testName, String[] data) throws Exception {
    String fileText = data[0];

    lexer.start(fileText);

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
}