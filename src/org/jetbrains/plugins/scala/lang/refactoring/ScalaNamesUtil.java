package org.jetbrains.plugins.scala.lang.refactoring;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.lexer.Lexer;
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.annotations.NotNull;

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.06.2008
 */
public class ScalaNamesUtil {
  public static boolean isIdentifier(String text) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (text == null) return false;

    Lexer lexer = new ScalaLexer();
    lexer.start(text, 0, text.length(), 0);
    if (lexer.getTokenType() != ScalaTokenTypes.tIDENTIFIER) return false;
    lexer.advance();
    return lexer.getTokenType() == null;
  }

  public static boolean isKeyword(@NotNull String text) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    Lexer lexer = new ScalaLexer();
    lexer.start(text,0,text.length(),0);
    if (lexer.getTokenType() == null || !ScalaTokenTypes.KEYWORDS.contains(lexer.getTokenType())) return false;
    lexer.advance();
    return lexer.getTokenType() == null;
  }
}
