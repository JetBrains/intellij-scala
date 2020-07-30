package org.jetbrains.plugins.scala
package editor
package typedHandler

import java.{util => ju}

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiWhiteSpace}
import org.jetbrains.plugins.scala.editor.AutoBraceUtils.{continuesConstructAfterIndentationContext, isBeforeIndentationContext}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScPostfixExpr
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.IndentUtil.calcIndent

object AutoBraceInsertionTools {
  def autoBraceInsertionActivated: Boolean =
    ScalaApplicationSettings.getInstance.HANDLE_BLOCK_BRACES_INSERTION_AUTOMATICALLY

  def shouldHandleAutoBracesBeforeTyped(typedChar: Char): Boolean = {
    autoBraceInsertionActivated &&
      !typedChar.isWhitespace &&
      typedChar != '{' && typedChar != '}'
  }

  private val continuesPreviousLine = Set('.')

  def findAutoBraceInsertionOpportunity(c: Option[Char], caretOffset: Int, element: PsiElement)
                                       (implicit project: Project, file: PsiFile, editor: Editor): Option[AutoBraceInsertionInfo] = {
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

          val tok = (PsiTreeUtil.prevVisibleLeaf(caretWS), c) match {
            case (tok: PsiElement, Some(c)) if isPossibleContinuationButWillNotBeContinuation(tok, c) => tok
            case (tok: PsiElement, None) if !couldBeContinuationAfterIndentationContext(tok.getText) => tok
            case _ => return None
          }

          val beforeContWS = PsiTreeUtil.prevLeaf(tok) match {
            case beforeContWS: PsiWhiteSpace => beforeContWS
            case _ => return None
          }

          val beforeContWSText = beforeContWS.getText
          val newlinePosBeforeCaret = beforeContWSText.lastIndexOf('\n')

          if (newlinePosBeforeCaret < 0) {
            // there is something else before the possible continuation, so do nothing
            return None
          }

          val beforeContIndent = beforeContWSText.substring(newlinePosBeforeCaret)
          (beforeContWS, caretWS, beforeContIndent, true)
        } else {
          if (c.exists(continuesPreviousLine)) {
            return None
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
            return None
          }

          // check if the new char is behind a postfix expression
          // in that case the postfix expr will be continued into an infix expression
          // but only check if there is now new line between the operator and the rhs operand
          val emptyNewlineBeforeCaret = caretWSText.lastIndexOf('\n', newlinePosBeforeCaret - 1) >= 0
          if (!emptyNewlineBeforeCaret && isBehindPostfixExpr(element)) {
            return None
          }

          val caretIndent = caretWSText.substring(newlinePosBeforeCaret, posInWs)
          (caretWS, caretWS, caretIndent, false)
        }
      case _ => return None
    }

    // ========= Get block that should be wrapped ==========
    // caret could be before or after the expression that should be wrapped
    val (expr, exprWS, caretIsBeforeExpr) = nextExpressionInIndentationContext(caretWS) match {
      case Some(expr) => (expr, caretWS, true)
      case None =>
        (previousExpressionInIndentationContext(wsBeforeCaret), c) match {
          case (Some(expr), Some(c)) if canBeContinuedWith(expr, c) =>
            // if we start typing something that could be a continuation of the previous construct, do not insert braces
            // for example:
            //
            // def test =
            //   if (cond) expr
            //   <caret>      <- when you type 'e', it could be the continuation of the previous if
            return None
          case (Some(expr), Some(c)) if indentationContextContinuation(expr).exists(_.toString.head == c) =>
            // if we start typing something that could be a continuation of a parent construct, do not insert braces
            // for example:
            // if (cond)
            //   expr
            //   <caret>      <- when you type 'e', it could be start of 'else' which would then be inside the block. so do nothing for now
            return None
          case (Some(expr), _) =>
            val exprWs = expr.prevElement match {
              case Some(ws: PsiWhiteSpace) => ws
              case _ => return None
            }
            (expr, exprWs, false)
          case _ =>
            return None
        }
    }
    val exprWSText = exprWS.getText

    // ========= Check correct indentation ==========
    val newlinePosBeforeExpr = exprWSText.lastIndexOf('\n')
    if (newlinePosBeforeExpr < 0) {
      return None
    }
    val exprIndent = exprWSText.substring(newlinePosBeforeExpr)

    if (exprIndent != curLineIndent) {
      return None
    }

    // ========= Calculate brace positions =========
    // Start with the opening brace, and then the closing brace.
    val document = editor.getDocument

    // ========= Opening brace =========
    val openingBraceOffset =
      exprWS
        .prevSiblingNotWhitespaceComment
        .fold(exprWS.startOffset)(_.endOffset)

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
    val (closingBraceOffset, isBeforeContinuation) =
      findClosingBraceOffsetBeforeContinuation(lastElement).map((_, true))
        .getOrElse(
          if (caretIsBeforeExpr) expr.endOffset -> false
          else caretOffset -> false
        )

    Some(AutoBraceInsertionInfo(
      openBraceOffset = openingBraceOffset,
      inputOffset = caretOffset,
      closingBraceOffset = closingBraceOffset,
      needsFakeInput = !isAfterPossibleContinuation,
      isBeforeContinuation = isBeforeContinuation
    ))
  }

  def findClosingBraceOffsetBeforeContinuation(lastElementBeforePossibleContinuation: PsiElement): Option[Int] =
    lastElementBeforePossibleContinuation.getNextNonWhitespaceAndNonEmptyLeaf match {
      case tok: PsiElement if continuesConstructAfterIndentationContext(tok) => Some(tok.startOffset)
      case _ => None
    }

  private val startsStatement = {
    import ScalaTokenType._
    import ScalaTokenTypes._
    Set(
      // modifier
      kABSTRACT,
      kCASE,
      kIMPLICIT,
      kFINAL,
      kLAZY,
      kOVERRIDE,
      kSEALED,
      //      InlineKeyword,    // can be used with if and match in scala 3
      TransparentKeyword,
      OpaqueKeyword,

      kVAL,
      kVAR,
      kDEF,
      GivenKeyword,

      kTYPE,
      ClassKeyword,
      TraitKeyword,
      ObjectKeyword,
      EnumKeyword,
      ExtensionKeyword,
    )
  }

  /**
   * Finds autobrace info for when the user types a statement where an expression is expected
   * For example
   *   def test =
   *     val<caret>   // now pressing space should insert braces, because it cannot be an expression
   *
   */
  def findAutoBraceInsertionOpportunityWhenStartingStatement(c: Char, caretOffset: Int, element: PsiElement)
                                                            (implicit project: Project, file: PsiFile, editor: Editor): Option[AutoBraceInsertionInfo] = {
    assert(c.isWhitespace)
    if (!autoBraceInsertionActivated) {
      return None
    }

    // check if the element before the caret certainly starts a statement
    val keyword = element.prevVisibleLeaf match {
      case Some(kw) if startsStatement(kw.elementType) => kw
      case _ => return None
    }

    // find out if this new statement is where an expression should be
    val elementWhereExprShouldBe = keyword.prevLeafNotWhitespaceComment match {
      case Some(last) if isBeforeIndentationContext(last) => last
      case _ => return None
    }

    val elementWithIndentationContext = elementWhereExprShouldBe.getParent
    val newlineBeforeKeyword = keyword.prevLeaf.exists(_.textContains('\n'))
    val tabSize = CodeStyle.getSettings(project).getTabSize(ScalaFileType.INSTANCE)

    //check indentation
    if (newlineBeforeKeyword && calcIndent(keyword, tabSize) > calcIndent(elementWithIndentationContext, tabSize)) {
      val document = editor.getDocument

      // find correct positions for braces when the current construct is
      // has a continuation
      val closingBraceOffsetBeforeContinuation =
        findClosingBraceOffsetBeforeContinuation(element)

      Some(AutoBraceInsertionInfo(
        openBraceOffset = elementWhereExprShouldBe.endOffset,
        inputOffset = caretOffset,
        closingBraceOffset = closingBraceOffsetBeforeContinuation.getOrElse(document.lineEndOffset(caretOffset)),
        needsFakeInput = false,
        isBeforeContinuation = closingBraceOffsetBeforeContinuation.isDefined
      ))
    } else None
  }

  def insertAutoBraces(info: AutoBraceInsertionInfo)(implicit project: Project, file: PsiFile, editor: Editor): Unit = {
    AutoBraceAdvertiser.disableNotification()

    // ========= Insert braces =========
    // Start with the opening brace, then the fake input, and finally the closing brace.
    // Also remember brace ranges for later reformatting
    val document = editor.getDocument

    // ========= Opening brace =========
    val openingBraceOffset = info.openBraceOffset
    document.insertString(openingBraceOffset, "{")
    val openingBraceRange = TextRange.from(openingBraceOffset, 1)
    val displacementAfterOpeningBrace = 1

    val caretOffset = info.inputOffset
    val displacementAfterFakeInput =
      if (info.needsFakeInput) {
        // We have to add a char at the position of the caret, otherwise formatting will screw up our indentation.
        // Unfortunately we have to delete it after formatting, so IDEA can do the char insertion correctly itself.
        // Also note that we cannot use c (parameter of this method) to insert it here, because some characters like '('
        // might confuse the formatter.
        val fakeInputPosition = caretOffset + displacementAfterOpeningBrace
        document.insertString(fakeInputPosition, "x")
        displacementAfterOpeningBrace + 1
      } else displacementAfterOpeningBrace

    val braceInsertPosition = info.closingBraceOffset + displacementAfterFakeInput
    val closingBraceRange =
      if (info.isBeforeContinuation) {
        document.insertString(braceInsertPosition, "} ")

        TextRange.from(braceInsertPosition, 3)
      } else {
        // There is no continuation, so just insert the braces after the caret or the expression
        document.insertString(braceInsertPosition, "\n}")
        TextRange.from(braceInsertPosition, 3)
      }
    document.commit(project)

    // Set the caret now, so the formatting can adjust it correctly
    val caretOffsetBeforeFormatting = caretOffset + displacementAfterOpeningBrace
    editor.getCaretModel.moveToOffset(caretOffsetBeforeFormatting)

    CodeStyleManager.getInstance(project)
      .reformatText(file, ju.Arrays.asList(openingBraceRange, closingBraceRange))

    if (info.needsFakeInput) {
      // Delete the fake character 'x' we inserted
      val newCaretPos = editor.getCaretModel.getOffset
      document.deleteString(newCaretPos, newCaretPos + 1)
      document.commit(project)
    }
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
  /*
    todo: use for caret being at the end of the file
    def isBehindPostfixExpr(element: Option[PsiElement], file: PsiFile): Boolean = {
      val prevElement = element.map(PsiTreeUtil.prevVisibleLeaf).orElse(file.lastChild)
      prevElement
        .exists { prev =>
          val start = element.fold(file.endOffset)(_.startOffset)
          prev
            .withParentsInFile
            .takeWhile(_.endOffset <= start)
            .exists(_.is[ScPostfixExpr])
        }
    }
   */

  /**
   * @param openBraceOffset       the position where the opening braces should be put
   * @param inputOffset           the position where fake input should be put if needsFakeInput is true
   * @param closingBraceOffset    the position where the closing brace should be put
   * @param needsFakeInput        if fake input is needed to get indentation correct for empty lines
   * @param isBeforeContinuation  if the closing will be added before some continuation like else, catch, finally
   */
  case class AutoBraceInsertionInfo(openBraceOffset: Int,
                                    inputOffset: Int,
                                    closingBraceOffset: Int,
                                    needsFakeInput: Boolean,
                                    isBeforeContinuation: Boolean)
}
