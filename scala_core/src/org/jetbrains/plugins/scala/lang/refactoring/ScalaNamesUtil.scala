package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import com.intellij.lexer.Lexer

/**
* User: Alexander Podkhalyuzin
* Date: 24.06.2008
*/

object ScalaNamesUtil {
  def isIdentifier(text: String): Boolean = {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (text == null) false;
    var lexer: Lexer = new ScalaLexer();
    lexer.start(text, 0, text.length(), 0);
    if (lexer.getTokenType() != ScalaTokenTypes.tIDENTIFIER) return false;
    lexer.advance();
    lexer.getTokenType() == null;
  }
  def isKeyword(text: String): Boolean = {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    var lexer: Lexer = new ScalaLexer();
    lexer.start(text, 0, text.length(), 0);
    if (lexer.getTokenType() == null || !ScalaTokenTypes.KEYWORDS.contains(lexer.getTokenType())) return false;
    lexer.advance();
    lexer.getTokenType() == null;
  }
}