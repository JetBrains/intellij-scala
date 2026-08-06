package org.jetbrains.plugins.scala.highlighter.lexer

import com.intellij.psi.tree.IElementType

/**
 * Can parse a non-interpolated multiline string literal """..."""
 */
final class ScalaMultilineStringLiteralLexer(
  quoteChar: Char,
  originalLiteralToken: IElementType,
  noUnicodeEscapesInRawStrings: Boolean
) extends ScalaStringLiteralRawAwareLexer(
  quoteChar,
  originalLiteralToken,
  supportsUnicodeEscapeSequence = !noUnicodeEscapesInRawStrings, //SCL-18631
  canEscapeEolOrFramingSpaces = true
) {

  /** Only unicode escape sequences are not supported in NON-interpolated multiline strings */
  override def getTokenType: IElementType =
    getTokenTypeForRawString

  /** see comment tp [[ScalaInterpolatedStringLiteralLexer.isIncompleteUnicodeEscapeSequenceAllowed]] */
  override protected def isIncompleteUnicodeEscapeSequenceAllowed: Boolean =
    true
}
