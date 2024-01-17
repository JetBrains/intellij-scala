package org.jetbrains.plugins.scala.highlighter.lexer

import com.intellij.psi.StringEscapesTokenTypes
import com.intellij.psi.tree.IElementType

/**
 * @param isRawOpt Some(..) when the lexer is used to parse tokens from a single string,
 *                 and when lexer is not reused in some nested injected strings
 *                 (like raw"outer ${s"inner"} outer").<br>
 *                 This hint is required when buffer passed to the lexer doesn't include the interpolator prefix itself and
 *                 starts directly from quotes.
 */
class ScalaInterpolatedStringLiteralLexer(
  quoteChar: Char,
  originalLiteralToken: IElementType,
  isRawLiteral: Boolean,
  isMultiline: Boolean,
) extends ScalaStringLiteralRawAwareLexer(quoteChar, originalLiteralToken, canEscapeEolOrFramingSpaces = isMultiline) {

  override def getTokenType: IElementType =
    if (isRawLiteral)
      getTokenTypeForRawString
    else {
      val token = super.getTokenType

      if (token == StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN &&
        myStart + 1 < myEnd &&
        (myBuffer.charAt(myStart + 1) == '\n' || myBuffer.charAt(myStart + 1) == ' ')
      ) {
        StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN
      } else {
        token
      }
    }

  /**
   * NOTE: We always allow incomplete incomplete unicode symbols in raw literals.
   * In reality incomplete unicode symbols are invalid only in some cases.
   * This behaviour is different before scala 2.13.2 and after.
   *
   * It looks more like compiler bugs, so I decided that it is not worth it to reproduce all those edge cases here.
   * The behaviour is fixed in Scala3: non kind of escaping is supported in raw interpolators
   *
   * @see [[https://github.com/scala/bug/issues/12294]]
   */
  override protected def isIncompleteUnicodeEscapeSequenceAllowed: Boolean =
    isRawLiteral
}