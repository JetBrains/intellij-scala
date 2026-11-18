package org.jetbrains.plugins.scala.editor.typedHandler

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate.Result
import com.intellij.codeInsight.{AutoPopupController, CodeInsightSettings}
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.{CodeStyleManager, CodeStyleSettings}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiElement, PsiErrorElement, PsiFile}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.editor.typedHandler.AutoBraceInsertionTools
import org.jetbrains.plugins.scala.editor.typedHandler.ScalaTypedHandler.BraceWrapInfo
import org.jetbrains.plugins.scala.editor.{AutoBraceAdvertiser, DocumentExt}
import org.jetbrains.plugins.scala.extensions.{CharSeqExt, ObjectExt, PsiElementExt, PsiFileExt, Whitespace}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionConfidence
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.util.IndentUtil

import java.util as ju
import scala.annotation.tailrec

/**
 * Backend implementation of [[ScalaTypingSupport]] for [[ScalaTypedHandler]].
 */
final class ScalaTypingSupportImpl extends ScalaTypingSupport {
  override def checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result =
    if (!file.is[ScalaFile]) Result.CONTINUE
    else if (charTyped == '{' || charTyped == '[') {
      AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null)
      Result.STOP
    }
    else if (file.isScala3File && charTyped == '"') {
      AutoPopupController.getInstance(project).scheduleAutoPopup(editor, (file: PsiFile) => {
        val offset = editor.getCaretModel.getOffset
        val leaf = file.findElementAt(offset)
        ScalaCompletionConfidence.isStringInUnionTypeExpectedPosition(leaf)
      })
      Result.STOP
    }
    else Result.CONTINUE

  override def scheduleAutoPopup(file: PsiFile, editor: Editor, project: Project): Unit =
    doScheduleAutoPopup(file, editor, project)

  override def startAutoPopupCompletion(file: PsiFile, editor: Editor)
                                       (document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    if (CodeInsightSettings.getInstance().AUTO_POPUP_COMPLETION_LOOKUP) {
      element.getParent match {
        case l: ScLiteral =>
          element.getNode.getElementType match {
            case ScalaTokenTypes.tSTRING | ScalaTokenTypes.tMULTILINE_STRING =>
              if (l.getText.count(_ == '$') == 1)
                scheduleAutoPopup(file, editor, project)
            case _ =>
          }
        case _ =>
      }
    }

  override def startAutoPopupCompletionInInterpolatedString(file: PsiFile, editor: Editor)
                                                           (document: Document, project: Project, element: PsiElement, offset: Int): Unit =
    if (CodeInsightSettings.getInstance().AUTO_POPUP_COMPLETION_LOOKUP) {
      element.getParent match {
        case _: ScLiteral =>
          element.getNode.getElementType match {
            case ScalaTokenTypes.tINTERPOLATED_STRING | ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING =>
              if (ScalaCompletionConfidence.isDotTypedAfterStringInjectedReference(file, offset)) {
                scheduleAutoPopup(file, editor, project)
              }
            case _ =>
          }
        case _ =>
      }
    }


  override def handleLeftBraceWrap(caretOffset: Int, element: PsiElement)
                                  (using project: Project, file: PsiFile, editor: Editor, settings: CodeStyleSettings): Boolean =
    ScalaTypedHandler.findElementToWrap(element) match {
      case Some(wrapInfo) =>
        if (!wrapInfo.wrapElement.is[ScBlockExpr])
          wrapWithBraces(caretOffset, wrapInfo)
        else false
      case _ => false
    }

  override def indentCaseKeyword(file: PsiFile)(document: Document, project: Project, element: PsiElement, offset: Int): Unit = {
    super.indentCaseKeyword(file)(document, project, element, offset)

    if (file.features.indentationBasedSyntaxEnabled) {
      alignNestedCaseClauseInBracelessSyntax(document, project, element)
    }
  }

  override def handleAutoBraces(char: Char, offset: Int, element: PsiElement)
                               (using project: Project, file: PsiFile, editor: Editor): Boolean =
    AutoBraceInsertionTools.findAutoBraceInsertionOpportunity(Some(char), offset, element)
      .fold(false) { info =>
        AutoBraceInsertionTools.insertAutoBraces(info)
        true
      }

  override def handleAutoBracesWhenStartingStatement(char: Char, offset: Int, element: PsiElement)
                                                    (using project: Project, file: PsiFile, editor: Editor): Boolean =
    AutoBraceInsertionTools.findAutoBraceInsertionOpportunityWhenStartingStatement(char, offset, element)
      .fold(false) { info =>
        AutoBraceInsertionTools.insertAutoBraces(info)
        true
      }

  /**
   * Keeps `case ` space-typing behavior aligned with Scala 2.
   *
   * In Scala 3 indentation-based syntax, an inner `case` after `case ... =>` may be parsed as a nested partial-function.
   * It's parsed in `ExprInIndentationRegion` and represented as [[ScBlockExpr]] rather than as a regular [[ScCaseClause]]<br>
   * Example: {{{
   *   val pf: PartialFunction[String, PartialFunction[Int, Boolean]] = {
   *     case "foo" =>
   *       case 1 => true // this is a nested partial function, it's not a sibling of `case "foo" =>`
   *   }
   * }}}
   *
   * In this case when we type `case 1` this check `indentKeyword[ScCaseClause]` will not detect the case.
   * And technically that would be true, technically we might don't need to insert the space as the user might want to
   * type a partial function manually.
   *
   * However, we still intentionally keep Scala 2-style unindent to preserve long-standing typing habits.<br>
   * We consider the nested partial function as a rare edge case (with no hard evidence though, just intuition).<br>
   * If a nested partial function is intended, the user can manually indent the inner `case`
   * or invoke the "Undo" action to remove the auto-added indentation.
   */
  private def alignNestedCaseClauseInBracelessSyntax(document: Document, project: Project, element: PsiElement): Unit = {
    val caseKeyword = Option(PsiTreeUtil.prevVisibleLeaf(element))
      .filter(_.elementType == ScalaTokenTypes.kCASE)
      .orNull
    if (caseKeyword == null) return

    val targetCaseClause = findTargetCaseClause(caseKeyword)
    if (targetCaseClause == null) return

    // If we reached here, the caret is in an inner `case` nested under an outer one.
    // Example:
    //   case "foo" =>
    //     case "bar" => // <== nearest previous `case` and its case clause (targetCaseClause)
    //       case <CARET>

    val targetIndent = getIndent(document, targetCaseClause)
    if (targetIndent == null) return

    adjustCaseIndentation(document, project, caseKeyword, targetIndent)
  }

  private def adjustCaseIndentation(
    document: Document,
    project: Project,
    caseKeyword: PsiElement,
    targetIndent: CharSequence
  ): Unit = {
    val currentCaseStart = caseKeyword.startOffset
    val currentLineStart = document.lineStartOffset(currentCaseStart)
    val currentIndent = document.getImmutableCharSequence.subSequence(currentLineStart, currentCaseStart)

    if (CharSequence.compare(currentIndent, targetIndent) != 0) {
      document.replaceString(currentLineStart, currentCaseStart, targetIndent)
      document.commit(project)
    }
  }

  @Nullable
  private def findTargetCaseClause(caseKeyword: PsiElement): ScCaseClause = {
    val previousCaseKeyword = PsiTreeUtil.prevVisibleLeaf(caseKeyword)
    if (previousCaseKeyword == null)
      return null

    previousCaseKeyword.getParent match {
      case clause: ScCaseClause => clause
      case _ => null
    }
  }

  @Nullable
  private def getIndent(document: Document, psiElement: PsiElement): CharSequence = {
    val startOffset = psiElement.startOffset
    val lineStartOffset = document.lineStartOffset(startOffset)
    val indent = document.getImmutableCharSequence.subSequence(lineStartOffset, startOffset)
    val indentContainsOnlySpaces = indent.forall(ch => ch == ' ' || ch == '\t')
    if (indentContainsOnlySpaces)
      indent
    else
      null
  }

  private def wrapWithBraces(caretOffset: Int, wrap: BraceWrapInfo)
                            (implicit project: Project, file: PsiFile, editor: Editor, settings: CodeStyleSettings): Boolean = {
    val document = editor.getDocument

    val BraceWrapInfo(wrapElement, beforeCaret, parent, canShareLine) = wrap

    val elementStartOffset = wrapElement.startOffset
    val elementLine = document.getLineNumber(elementStartOffset)
    val prevElementLine = document.getLineNumber(beforeCaret.startOffset)
    val caretLine = document.getLineNumber(caretOffset)

    val caretIsBeforeElement = caretOffset <= elementStartOffset
    val caretAndPrevElementOnSameLine = caretLine == prevElementLine
    val singleLineDefinition = prevElementLine == elementLine

    val needToHandle = caretIsBeforeElement &&
      caretAndPrevElementOnSameLine &&
      isElementIndented(parent, wrapElement) &&
      !followedByBrokenClosingBrace(wrapElement)
    if (needToHandle) {
      document.insertString(caretOffset, "{")

      if (singleLineDefinition) {
        // if left brace is inserted on the same line with body we expect the user to press Enter after that
        // in this case we rely that EnterAfterUnmatchedBraceHandler will insert missing closing brace
        editor.getCaretModel.moveToOffset(caretOffset + 1)
      }
      else {
        val endElement = advanceElementToLineComment(wrapElement)
        val elementActualStartOffset = elementStartOffset + 1
        val elementActualEndOffset = endElement.endOffset + 1
        editor.getCaretModel.moveToOffset(elementActualStartOffset)

        if (canShareLine) {
          endElement.getNextSibling match {
            case ws@Whitespace(wsText) =>
              val lastNewLineIdx = wsText.lastIndexOf("\n").max(0)
              document.deleteString(ws.startOffset + lastNewLineIdx + 1, ws.endOffset + 1)
            case _ =>
          }
        }
        val insertedClosingBraceWithNl = "\n}"
        document.insertString(elementActualEndOffset, insertedClosingBraceWithNl)
        document.commit(project)

        val ranges = ju.Arrays.asList(
          TextRange.from(caretOffset, 1),
          //NOTES:
          // extra `+1` is required to cover space after `}`
          // e.g. we want a space to be inserted before `else` in `if (...) {...} else` when `}` is inserted
          // (it's actual for many other constructs, see SCL-15549)
          // `.min(document.getTextLength)` is required inn order range doesn't exceed document length,
          // in case it's simple block without any continuation
          TextRange.create(elementActualEndOffset, (elementActualEndOffset + insertedClosingBraceWithNl.length + 1).min(document.getTextLength))
        )
        CodeStyleManager.getInstance(project).reformatText(file, ranges)
      }

      AutoBraceAdvertiser.advertiseAutoBraces(project)
      true
    } else {
      false
    }
  }

  /** @return line comment following the element if exists or original element */
  private def advanceElementToLineComment(element: PsiElement): PsiElement = {
    @tailrec
    def inner(el: PsiElement): PsiElement = {
      PsiTreeUtil.nextLeaf(el) match {
        case comment: PsiComment if comment.elementType == ScalaTokenTypes.tLINE_COMMENT => comment
        case ws@Whitespace(text) if !text.contains("\n") => inner(ws)
        case _ => element
      }
    }

    inner(element)
  }

  private def isElementIndented(parent: PsiElement, child: PsiElement)(implicit settings: CodeStyleSettings): Boolean = {
    val tabSize = settings.getTabSize(ScalaFileType.INSTANCE)
    IndentUtil.compare(child, parent, tabSize) > 0
  }

  private def followedByBrokenClosingBrace(element: PsiElement): Boolean = {
    val next = element.getNextNonWhitespaceAndNonEmptyLeaf
    val isClosingBrace = next != null && next.elementType == ScalaTokenTypes.tRBRACE
    isClosingBrace && PsiTreeUtil.nextLeaf(next).is[PsiErrorElement]
  }
}
