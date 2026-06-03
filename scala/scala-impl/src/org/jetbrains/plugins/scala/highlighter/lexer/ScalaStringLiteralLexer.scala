package org.jetbrains.plugins.scala.highlighter.lexer

import com.intellij.lexer.StringLiteralLexer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.StringEscapesTokenTypes
import com.intellij.psi.tree.IElementType

/** @see [[org.jetbrains.plugins.scala.lang.psi.impl.base.literals.escapers.ScalaStringParser]] */
class ScalaStringLiteralLexer(
  quoteChar: Char,
  originalLiteralToken: IElementType,
  canEscapeEolOrFramingSpaces: Boolean = false
) extends StringLiteralLexer(quoteChar, originalLiteralToken, canEscapeEolOrFramingSpaces, null) {

  override protected def getUnicodeEscapeSequenceType: IElementType = {
    var start = myStart + 2

    // \uuuu0025 is also valid
    while (start < myEnd && myBuffer.charAt(start) == 'u')
      start += 1

    val isValid = start + 3 < myEnd &&
      StringUtil.isHexDigit(myBuffer.charAt(start)) &&
      StringUtil.isHexDigit(myBuffer.charAt(start + 1)) &&
      StringUtil.isHexDigit(myBuffer.charAt(start + 2)) &&
      StringUtil.isHexDigit(myBuffer.charAt(start + 3))

    if (isValid)
      StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN
    else if (isIncompleteUnicodeEscapeSequenceAllowed)
      myOriginalLiteralToken
    else
      StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN
  }

  protected def isIncompleteUnicodeEscapeSequenceAllowed = false

  // NOTE: since 2.13.2 unicode characters are processed lately, so
  //  - before 2.13.2 "\005c\005c" == "\\" (like in Java)
  //  - after 2.13.2 "\005c\005c" == "\\\\"
  // here wo stick to the latest version and don't handle all scala versions
  override protected def locateUnicodeEscapeSequence(start: Int, i0: Int): Int = {
    var i = i0
    do i += 1
    while (i < myBufferEnd && myBuffer.charAt(i) == 'u')
    parseUnicodeDigits(i)
  }

  private def parseUnicodeDigits(i0: Int): Int = {
    var i = i0
    val end = i + 4
    while (i < end) {
      if (i == myBufferEnd) return i
      if (!StringUtil.isHexDigit(myBuffer.charAt(i))) return i
      i += 1
    }
    end
  }
}