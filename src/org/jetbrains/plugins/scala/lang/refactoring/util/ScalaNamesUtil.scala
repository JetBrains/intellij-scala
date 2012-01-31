package org.jetbrains.plugins.scala
package lang
package refactoring
package util

import com.intellij.openapi.application.ApplicationManager
import lexer.{ScalaLexer, ScalaTokenTypes}

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.06.2008
 */
object ScalaNamesUtil {
  private def checkGeneric(text: String, predicate: ScalaLexer => Boolean): Boolean = {
    ApplicationManager.getApplication.assertReadAccessAllowed()
    if (text == null || text == "") return false
    
    val lexer = new ScalaLexer()
    lexer.start(text, 0, text.length(), 0)
    if (!predicate(lexer)) return false
    lexer.advance()
    lexer.getTokenType == null
  }

  private def isOpCharacter(c : Char) : Boolean = {
    c match {
      case '~' | '!' | '@' | '#' | '%' | '^' | '*' | '+' | '-' | '<' | '>' | '?' | ':' | '=' | '&' | '|' | '/' | '\\' =>
        true
      case ch =>
        Character.getType(ch) == Character.MATH_SYMBOL.toInt || Character.getType(ch) == Character.OTHER_SYMBOL.toInt
    }
  }

  def isIdentifier(text: String): Boolean = {
    checkGeneric(text, lexer => lexer.getTokenType == ScalaTokenTypes.tIDENTIFIER)
  }

  def isKeyword(text: String): Boolean = {
    checkGeneric(text, lexer => lexer.getTokenType != null && ScalaTokenTypes.KEYWORDS.contains(lexer.getTokenType))
  }
  
  def isOperatorName(text: String): Boolean = isIdentifier(text) && isOpCharacter(text(0))
}
