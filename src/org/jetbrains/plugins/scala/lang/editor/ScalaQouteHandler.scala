package org.jetbrains.plugins.scala.lang.editor

import com.intellij.codeInsight.editorActions.QuoteHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

/**
* User: Alexander.Podkhalyuzin
* Date: 30.05.2008
*/

class ScalaQuoteHandler extends QuoteHandler {
  def isClosingQuote(iterator: HighlighterIterator, offset: Int): Boolean = {
    val token = iterator.getTokenType
    if (token == ScalaTokenTypes.tSTRING ||
            token == ScalaTokenTypes.tCHAR) {
      val start = iterator.getStart();
      val end = iterator.getEnd();
      return end - start >= 1 && offset == end - 1
    }
    return false
  }
  def isOpeningQuote(iterator: HighlighterIterator, offset: Int): Boolean = {
    val token = iterator.getTokenType
    if (token == ScalaTokenTypes.tSTRING ||
            token == ScalaTokenTypes.tSTUB) {
      val start = iterator.getStart();
      return offset == start
    }
    return false
  }
  def hasNonClosedLiteral(editor: Editor, iterator: HighlighterIterator, offset: Int): Boolean = {
    return true
  }
  def isInsideLiteral(iterator: HighlighterIterator): Boolean = {
    val token = iterator.getTokenType
    return token == ScalaTokenTypes.tSTRING ||
            token == ScalaTokenTypes.tCHAR
  }
}