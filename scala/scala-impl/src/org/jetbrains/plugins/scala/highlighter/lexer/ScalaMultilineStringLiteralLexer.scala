package org.jetbrains.plugins.scala.highlighter.lexer

import com.intellij.psi.tree.IElementType

class ScalaMultilineStringLiteralLexer(
  quoteChar: Char,
  originalLiteralToken: IElementType,
) extends ScalaStringLiteralRawAwareLexer(quoteChar, originalLiteralToken, canEscapeEolOrFramingSpaces = true) {

  /** Only unicode escape sequences are not supported in NON-interpolated multiline strings */
  override def getTokenType: IElementType =
    getTokenTypeForRawString

  /** see comment tp [[ScalaInterpolatedStringLiteralLexer.isIncompleteUnicodeEscapeSequenceAllowed]] */
  override protected def isIncompleteUnicodeEscapeSequenceAllowed: Boolean =
    true
}
