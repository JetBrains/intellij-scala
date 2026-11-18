package org.jetbrains.plugins.scala
package codeInsight
package editorActions

import com.intellij.codeInsight.editorActions.{JavaLikeQuoteHandler, MultiCharQuoteHandler}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.util.{ProperTextRange, TextRange}
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.{IElementType, TokenSet}
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.MultilineStringUtil.MultilineQuotes

final class ScalaQuoteHandler extends JavaLikeQuoteHandler with MultiCharQuoteHandler {

  import ScalaQuoteHandler._

  override def isClosingQuote(iterator: HighlighterIterator, offset: Int): Boolean =
    iterator.getTokenType match {
      case `tSTRING` | `tCHAR` => iterator.getStart <= offset && offset == iterator.getEnd - 1
      case `tMULTILINE_STRING` => iterator.getEnd - offset <= 3
      case `tINTERPOLATED_STRING_END` => true
      case _ => false
    }

  override def isOpeningQuote(iterator: HighlighterIterator, offset: Int): Boolean =
    iterator.getStart == offset && {
      iterator.getTokenType match {
        case `tWRONG_STRING` |
             `tINTERPOLATED_STRING` => true
        case `tSTUB` =>
          textInRange(iterator, TextRange.from(offset, 1)) match {
            case "`" | "'" => true
            case _ => false
          }
        case _ => false
      }
    }

  override def hasNonClosedLiteral(editor: Editor, iterator: HighlighterIterator, offset: Int) = true

  override def isInsideLiteral(iterator: HighlighterIterator): Boolean =
    iterator.getTokenType match {
      case `tSTRING` |
           `tCHAR` |
           `tMULTILINE_STRING` |
           `tINTERPOLATED_STRING` => true
      case _ => false
    }

  override def getConcatenatableStringTokenTypes: TokenSet = TokenSet.create(tSTRING)

  override def getStringConcatenationOperatorRepresentation = "+"

  override def getStringTokenTypes: TokenSet = TokenSet.create(tSTRING, tINTERPOLATED_STRING)

  override def isAppropriateElementTypeForLiteral(tokenType: IElementType): Boolean =
    tokenType match {
      case `tSEMICOLON` |
           `tCOMMA` |
           `tRPARENTHESIS` |
           `tRSQBRACKET` |
           `tRBRACE` |
           `tSTRING` |
           // to be able to complete empty interpolated string s"" or s"""""" in the very end of the file
           `tIDENTIFIER` | `tINTERPOLATED_STRING_END` |
           `tCHAR` => true
      case _ =>
        COMMENTS_TOKEN_SET.contains(tokenType) ||
          WHITES_SPACES_TOKEN_SET.contains(tokenType)
    }

  override def needParenthesesAroundConcatenation(element: PsiElement): Boolean =
    element.getParent match {
      case (_: ScLiteral) childOf (_: ScReferenceExpression) => true
      case _ => false
    }

  // NOTE: despite the naming of the method it only handles closing quote for multiline string literals
  override def getClosingQuote(iterator: HighlighterIterator, offset: Int): String =
    if (ScalaApplicationSettings.getInstance.INSERT_MULTILINE_QUOTES &&
      rangeFromEnd(offset, MultilineQuotes).exists { range =>
        canStartWithMultilineQuotes(iterator) && textInRange(iterator, range) == MultilineQuotes
      })
      MultilineQuotes
    else
      null
}

object ScalaQuoteHandler {

  private def rangeFromEnd(endOffset: Int, text: String): Option[ProperTextRange] =
    endOffset - text.length match {
      case startOffset if startOffset >= 0 => Some(new ProperTextRange(startOffset, endOffset))
      case _ => None
    }

  private def textInRange(iterator: HighlighterIterator,
                          range: TextRange) =
    iterator.getDocument.getText(range)

  private def canStartWithMultilineQuotes(iterator: HighlighterIterator) =
    iterator.getTokenType match {
      case `tINTERPOLATED_MULTILINE_STRING` | `tMULTILINE_STRING` => true
      case _ =>
        // hack with optional retreat before WRONG_STRING check is required cause highlighter behaves awkwardly
        // in case of empty file (see CompleteMultilineStringTest.testCompleteMultiCaret_EmptyFileWithEmptyEndLine)
        // TODO: simplify when parsing of incomplete multiline strings is unified for interpolated and non-interpolated strings
        //  to understand what is happening here just try to open psi tree viewer and see how incomplete `s"""` or `"""` are parsed
        (iterator.getTokenType == tWRONG_STRING || iterator.getStart > 0 && {
          iterator.retreat()
          iterator.getTokenType == tWRONG_STRING
        }) && iterator.getStart > 0 && {
          iterator.retreat()
          iterator.getTokenType == tSTRING
        }
    }
}