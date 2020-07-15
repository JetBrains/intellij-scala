package org.jetbrains.plugins.scala
package editor
package typedHandler

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate.Result
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiFile, PsiWhiteSpace}
import com.intellij.psi.codeStyle.{CodeStyleManager, CodeStyleSettings}
import com.intellij.psi.util.PsiTreeUtil
import extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScPostfixExpr
import java.{util => ju}

import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

trait AutoBraceInserter {
  def autoBraceInsertionActivated: Boolean =
    ScalaApplicationSettings.getInstance.HANDLE_BLOCK_BRACES_INSERTION_AUTOMATICALLY

  def shouldHandleAutoBracesBeforeTyped(typedChar: Char): Boolean = {
    autoBraceInsertionActivated &&
      !typedChar.isWhitespace &&
      typedChar != '{' && typedChar != '}'
  }

  private val continuesPreviousLine = Set('.')

  def handleAutoBraces(c: Char, caretOffset: Int, element: PsiElement)
                      (implicit project: Project, file: PsiFile, editor: Editor, settings: CodeStyleSettings): Result = {
    import AutoBraceUtils._

    val (wsBeforeCaret, caretWS, curLineIndent, isAfterPossibleContinuation) = element match {
      case caretWS: PsiWhiteSpace =>
        if (caretOffset == caretWS.startOffset) {
          // There is a possible continuation before the caret (like els(e)/catc(h)/fina(lly)
          // We now have to take the whitespace before and after this token into account
          //   if (cond)
          //     expr
          //     els<caret>
          //  ^       ^caretWS
          //  ^beforeContWS
          def isPossibleContinuationButWillNotBeContinuation(tok: PsiElement, addedChar: Char): Boolean = {
            val tokText = tok.getText
            couldBeContinuationAfterIndentationContext(tokText) &&
              !couldBeContinuationAfterIndentationContext(tokText + addedChar)
          }

          val tok = PsiTreeUtil.prevVisibleLeaf(caretWS) match {
            case tok: PsiElement if isPossibleContinuationButWillNotBeContinuation(tok, c) => tok
            case _ => return Result.CONTINUE
          }

          val beforeContWS = PsiTreeUtil.prevLeaf(tok) match {
            case beforeContWS: PsiWhiteSpace => beforeContWS
            case _ => return Result.CONTINUE
          }

          val beforeContWSText = beforeContWS.getText
          val newlinePosBeforeCaret = beforeContWSText.lastIndexOf('\n')

          if (newlinePosBeforeCaret < 0) {
            // there is something else before the possible continuation, so do nothing
            return Result.CONTINUE
          }

          val beforeContIndent = beforeContWSText.substring(newlinePosBeforeCaret)
          (beforeContWS, caretWS, beforeContIndent, true)
        } else {
          if (continuesPreviousLine(c)) {
            return Result.CONTINUE
          }

          // There is no possible continuation before the caret, so check if it looks like this
          //   def test =
          //     expr
          //     <caret>
          val caretWSText = caretWS.getText
          val posInWs = caretOffset - element.getTextOffset
          val newlinePosBeforeCaret = caretWSText.lastIndexOf('\n', posInWs - 1)

          if (newlinePosBeforeCaret < 0) {
            // there is something before the caret, so do nothing
            return Result.CONTINUE
          }

          // check if the new char is behind a postfix expression
          // in that case the postfix expr will be continued into an infix expression
          // but only check if there is now new line between the operator and the rhs operand
          val emptyNewlineBeforeCaret = caretWSText.lastIndexOf('\n', newlinePosBeforeCaret - 1) >= 0
          if (!emptyNewlineBeforeCaret && isBehindPostfixExpr(element)) {
            return Result.CONTINUE
          }

          val caretIndent = caretWSText.substring(newlinePosBeforeCaret, posInWs)
          (caretWS, caretWS, caretIndent, false)
        }
      case _ => return Result.CONTINUE
    }

    // ========= Get block that should be wrapped ==========
    // caret could be before or after the expression that should be wrapped
    val (expr, exprWS, caretIsBeforeExpr) = nextExpressionInIndentationContext(caretWS) match {
      case Some(expr) => (expr, caretWS, true)
      case None =>
        previousExpressionInIndentationContext(wsBeforeCaret) match {
          case Some(expr) if canBeContinuedWith(expr, c) =>
            // if we start typing something that could be a continuation of the previous construct, do not insert braces
            // for example:
            //
            // def test =
            //   if (cond) expr
            //   <caret>      <- when you type 'e', it could be the continuation of the previous if
            return Result.CONTINUE
          case Some(expr) if  indentationContextContinuation(expr).exists(_.toString.head == c) =>
            // if we start typing something that could be a continuation of a parent construct, do not insert braces
            // for example:
            // if (cond)
            //   expr
            //   <caret>      <- when you type 'e', it could be start of 'else' which would then be inside the block. so do nothing for now
            return Result.CONTINUE
          case Some(expr) =>
            val exprWs = expr.prevElement match {
              case Some(ws: PsiWhiteSpace) => ws
              case _ => return Result.CONTINUE
            }
            (expr, exprWs, false)
          case None =>
            return Result.CONTINUE
        }
    }
    val exprWSText = exprWS.getText

    // ========= Check correct indention ==========
    val newlinePosBeforeExpr = exprWSText.lastIndexOf('\n')
    if (newlinePosBeforeExpr < 0) {
      return Result.CONTINUE
    }
    val exprIndent = exprWSText.substring(newlinePosBeforeExpr)

    if (exprIndent != curLineIndent) {
      return Result.CONTINUE
    }

    // ========= Insert braces =========
    // Start with the opening brace, and then the closing brace.
    // Also remember brace ranges for later reformating
    val document = editor.getDocument

    val openingBraceOffset =
      exprWS
        .prevSiblingNotWhitespaceComment
        .fold(exprWS.startOffset)(_.endOffset)
    // ========= Opening brace =========
    document.insertString(openingBraceOffset, "{")
    val openingBraceRange = TextRange.from(openingBraceOffset, 1)
    val displacementAfterOpeningBrace = 1

    // ========= Closing brace =========
    // After the caret there could many whitespaces and then something that
    // proceeds to complete the parent of expr for example in
    //   if (cond)
    //     expr
    //     <caret>
    //
    //   else
    //     elseExpr
    //
    // In this case the braces should be added before the else and not directly after the caret
    val lastElement = if (caretIsBeforeExpr) expr else caretWS
    val subsequentConstructOffset = lastElement.getNextNonWhitespaceAndNonEmptyLeaf match {
      case tok: PsiElement if continuesConstructAfterIndentationContext(tok) => Some(tok.startOffset)
      case _ => None
    }

    // We have to add a char at the position of the caret, otherwise formatting will screw up our indentation.
    // And we cannot use Result.STOP because that would interfere with other handlers of typedChar afterwards.
    // Unfortunately we have to delete it after formatting, so IDEA can do the char insertion correctly itself.
    // Also note that we cannot use c (parameter of this method) to insert it here, because some characters like '('
    // might confuse the formatter.
    val fakeInputPosition = caretOffset + displacementAfterOpeningBrace
    document.insertString(fakeInputPosition, "x")
    val displacementAfterFakeInput = displacementAfterOpeningBrace + 1

    val closingBraceRange = subsequentConstructOffset match {
      case Some(subsequentConstructOffset) =>
        val braceInsertPosition = subsequentConstructOffset + displacementAfterFakeInput
        document.insertString(braceInsertPosition, "} ")

        TextRange.from(braceInsertPosition, 3)
      case None =>
        // There is no subsequent construct, so just insert the braces after the caret or the expression
        val braceInsertPosition =
          if (caretIsBeforeExpr) expr.endOffset + displacementAfterFakeInput
          else caretOffset + displacementAfterFakeInput

        document.insertString(braceInsertPosition, "\n}")
        TextRange.from(braceInsertPosition, 3)
    }
    document.commit(project)

    // Set the caret now, so the formatting can adjust it correctly
    val caretOffsetBeforeFormatting = caretOffset + displacementAfterOpeningBrace
    editor.getCaretModel.moveToOffset(caretOffsetBeforeFormatting)

    CodeStyleManager.getInstance(project)
      .reformatText(file, ju.Arrays.asList(openingBraceRange, closingBraceRange))

    // Delete the fake character 'x' we inserted
    val newCaretPos = editor.getCaretModel.getOffset
    document.deleteString(newCaretPos, newCaretPos + 1)

    // prevent other beforeTyped-handlers from being executed because psi tree is out of sync now
    Result.DEFAULT
  }

  def isBehindPostfixExpr(element: PsiElement): Boolean = {
    val start = element.startOffset
    PsiTreeUtil.prevVisibleLeaf(element).nullSafe
      .exists(prev =>
        prev
          .withParentsInFile
          .takeWhile(_.endOffset <= start)
          .exists(_.is[ScPostfixExpr])
      )
  }

  def continuesPostfixExpr(offset: Int, element: PsiElement, document: Document): Boolean = {
    val caretLine = document.getLineNumber(offset)
    val lastElementLine = document.getLineNumber(element.startOffset)
    (caretLine - lastElementLine == 1) &&
      isBehindPostfixExpr(element)
  }
}
