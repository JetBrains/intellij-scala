package org.jetbrains.plugins.scala
package lang
package refactoring
package util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.lexer.Lexer
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.06.2008
 */
object ScalaNamesUtil {
  def isIdentifier(text: String): Boolean = {
    ApplicationManager.getApplication.assertReadAccessAllowed
    if (text == null) return false

    val lexer = new ScalaLexer();
    lexer.start(text, 0, text.length, 0)
    if (lexer.getTokenType != ScalaTokenTypes.tIDENTIFIER) return false
    lexer.advance
    lexer.getTokenType == null
  }

  def isKeyword(text: String): Boolean = {
    ApplicationManager.getApplication.assertReadAccessAllowed

    val lexer = new ScalaLexer
    lexer.start(text,0,text.length,0)
    if (lexer.getTokenType == null || !ScalaTokenTypes.KEYWORDS.contains(lexer.getTokenType)) return false
    lexer.advance
    lexer.getTokenType == null
  }
}
