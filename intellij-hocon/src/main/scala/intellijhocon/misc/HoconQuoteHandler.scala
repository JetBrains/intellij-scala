package intellijhocon
package misc

import com.intellij.codeInsight.editorActions.{JavaLikeQuoteHandler, SimpleTokenSetQuoteHandler}
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.psi.tree.{TokenSet, IElementType}
import com.intellij.psi.PsiElement
import intellijhocon.lexer.HoconTokenType

class HoconQuoteHandler extends SimpleTokenSetQuoteHandler(HoconTokenType.QuotedString) with JavaLikeQuoteHandler {

  override protected def isNonClosedLiteral(iterator: HighlighterIterator, chars: CharSequence) =
    iterator.getStart >= iterator.getEnd - 1 || chars.charAt(iterator.getEnd - 1) != '\"'

  def getConcatenatableStringTokenTypes = TokenSet.EMPTY

  def getStringConcatenationOperatorRepresentation = null

  def getStringTokenTypes = myLiteralTokenSet

  def needParenthesesAroundConcatenation(element: PsiElement) = false

  def isAppropriateElementTypeForLiteral(tokenType: IElementType) = true

}
