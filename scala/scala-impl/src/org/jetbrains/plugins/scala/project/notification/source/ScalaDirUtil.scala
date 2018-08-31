package org.jetbrains.plugins.scala
package project.notification.source

import java.lang.StringBuilder

import com.intellij.lexer.Lexer
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.lang.lexer.{ScalaLexer, ScalaTokenTypes}

/**
 * @author Alexander Podkhalyuzin
 */
object ScalaDirUtil {
  def getPackageStatement(text: CharSequence): String = {
    val lexer: Lexer = new ScalaLexer
    lexer.start(text)
    val buffer: StringBuilder = new StringBuilder
    def readPackage(firstTime: Boolean) {
      skipWhiteSpaceAndComments(lexer)
      if (lexer.getTokenType != ScalaTokenTypes.kPACKAGE) return
      if (!firstTime) buffer.append('.')
      lexer.advance()
      skipWhiteSpaceAndComments(lexer)
      if (lexer.getTokenType == ScalaTokenTypes.kOBJECT) {
        lexer.advance()
        skipWhiteSpaceAndComments(lexer)
        if (lexer.getTokenType == ScalaTokenTypes.tIDENTIFIER)
          buffer.append(text, lexer.getTokenStart, lexer.getTokenEnd)
        return
      }
      def appendPackageStatement() {
        while (true) {
          if (lexer.getTokenType != ScalaTokenTypes.tIDENTIFIER) return
          buffer.append(text, lexer.getTokenStart, lexer.getTokenEnd)
          lexer.advance()
          skipWhiteSpaceAndComments(lexer)
          if (lexer.getTokenType != ScalaTokenTypes.tDOT) return
          buffer.append('.')
          lexer.advance()
          skipWhiteSpaceAndComments(lexer)
        }
      }
      appendPackageStatement()
      if (lexer.getTokenType == ScalaTokenTypes.tLBRACE) {
        lexer.advance()
        skipWhiteSpaceAndComments(lexer)
      }
      readPackage(false)
    }
    readPackage(true)
    val packageName: String = buffer.toString
    if (packageName.length == 0 || StringUtil.endsWithChar(packageName, '.')) return null
    packageName
  }

  def skipWhiteSpaceAndComments(lexer: Lexer) {
    while (ScalaTokenTypes.WHITES_SPACES_AND_COMMENTS_TOKEN_SET.contains(lexer.getTokenType)) {
      lexer.advance()
    }
  }
}

