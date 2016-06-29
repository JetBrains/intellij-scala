package org.jetbrains.plugins.hocon.editor

import com.intellij.codeInsight.editorActions.{JavaLikeQuoteHandler, SimpleTokenSetQuoteHandler}
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.{IElementType, TokenSet}
import org.jetbrains.plugins.hocon.lexer.HoconTokenType

class HoconQuoteHandler extends SimpleTokenSetQuoteHandler(HoconTokenType.QuotedString) with JavaLikeQuoteHandler {

  override protected def isNonClosedLiteral(iterator: HighlighterIterator, chars: CharSequence) =
    iterator.getStart >= iterator.getEnd - 1 || chars.charAt(iterator.getEnd - 1) != '\"'

  def getConcatenatableStringTokenTypes = TokenSet.EMPTY

  def getStringConcatenationOperatorRepresentation = null

  def getStringTokenTypes: TokenSet = myLiteralTokenSet

  def needParenthesesAroundConcatenation(element: PsiElement) = false

  def isAppropriateElementTypeForLiteral(tokenType: IElementType) = true

}
