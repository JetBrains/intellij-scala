package org.jetbrains.plugins.scala.codeInsight.editorActions

import com.intellij.codeInsight.editorActions.JavaLikeQuoteHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.{IElementType, TokenSet}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

class ScalaQuoteHandler extends JavaLikeQuoteHandler {

  override def isClosingQuote(iterator: HighlighterIterator, offset: Int): Boolean =
    iterator.getTokenType match {
      case `tSTRING` | `tCHAR` | `tINTERPOLATED_STRING_END` =>
        iterator.getStart <= offset && offset == iterator.getEnd - 1
      case _                                                => false
    }

  override def isOpeningQuote(iterator: HighlighterIterator, offset: Int): Boolean =
    iterator.getTokenType match {
      case `tWRONG_STRING` |
           `tINTERPOLATED_STRING` => offset == iterator.getStart
      case _                      => false
    }

  override def hasNonClosedLiteral(editor: Editor, iterator: HighlighterIterator, offset: Int) = true

  override def isInsideLiteral(iterator: HighlighterIterator): Boolean =
    iterator.getTokenType match {
      case `tSTRING` |
           `tCHAR` |
           `tMULTILINE_STRING` |
           `tINTERPOLATED_STRING` => true
      case _                      => false
    }

  override def getConcatenatableStringTokenTypes: TokenSet = TokenSet.create(tSTRING)

  override def getStringConcatenationOperatorRepresentation = "+"

  override def getStringTokenTypes: TokenSet = TokenSet.create(tSTRING, tINTERPOLATED_STRING)

  override def isAppropriateElementTypeForLiteral(tokenType: IElementType): Boolean = {
    tokenType match {
      case `tSEMICOLON` |
           `tCOMMA` |
           `tRPARENTHESIS` |
           `tRSQBRACKET` |
           `tRBRACE` |
           `tSTRING` |
           `tCHAR` => true
      case _       =>
        COMMENTS_TOKEN_SET.contains(tokenType) ||
          WHITES_SPACES_TOKEN_SET.contains(tokenType)
    }
  }

  override def needParenthesesAroundConcatenation(element: PsiElement): Boolean = {
    val parent = element.getParent
    parent.isInstanceOf[ScLiteral] && parent.getParent.isInstanceOf[ScReferenceExpression]
  }
}