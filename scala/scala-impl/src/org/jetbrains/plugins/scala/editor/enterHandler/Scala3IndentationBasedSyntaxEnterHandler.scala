package org.jetbrains.plugins.scala.editor.enterHandler

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.formatting.IndentInfo
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.{Document, Editor, EditorModificationUtilEx}
import com.intellij.openapi.util.Ref
import com.intellij.psi._
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.DocumentUtil
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.plugins.scala.editor.enterHandler.Scala3IndentationBasedSyntaxEnterHandler._
import org.jetbrains.plugins.scala.editor.{DocumentExt, ScalaEditorUtils, ScalaIndentationSyntaxUtils}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.highlighter.ScalaCommenter
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtensionBody
import org.jetbrains.plugins.scala.lang.scaladoc.ScalaIsCommentComplete
import org.jetbrains.plugins.scala.util.IndentUtil

/**
 * Other indentation-related Platform logic:
 *  - [[com.intellij.codeInsight.editorActions.EnterHandler#executeWriteActionInner]]
 *  - [[com.intellij.formatting.FormatProcessor#getIndent]]
 *  - [[com.intellij.psi.codeStyle.lineIndent.LineIndentProvider#getLineIndent]]<br>
 *    [[com.intellij.psi.impl.source.codeStyle.lineIndent.IndentCalculator#getIndentString]]
 *    (when [[com.intellij.psi.impl.source.codeStyle.lineIndent.JavaLikeLangLineIndentProvider]] is used)
 *
 * Other indentation-related Scala Plugin logic:
 *  - [[org.jetbrains.plugins.scala.lang.formatting.ScalaBlock.getChildAttributes]]<br>
 *    used to calculate alignment and indent for new blocks when pressing Enter
 *  - [[org.jetbrains.plugins.scala.lang.formatting.processors.ScalaIndentProcessor.getChildIndent]]
 *    used to calculate indent for existing elements
 *  - [[org.jetbrains.plugins.scala.lang.formatting.ScalaBlock.isIncomplete]]<br>
 *    used when typing after incomplete block, in the beginning of some structure, e.g.: {{{
 *      def foo = <caret>
 *    }}}
 *  - [[org.jetbrains.plugins.scala.editor.ScalaLineIndentProvider.getLineIndent]]
 *
 *  Also see [[org.jetbrains.plugins.scala.editor.backspaceHandler.Scala3IndentationBasedSyntaxBackspaceHandler]]
 */
class Scala3IndentationBasedSyntaxEnterHandler extends EnterHandlerDelegateAdapter {

  // NOTE: maybe we could move some logic here? investigate whether it has any advantages
  override def invokeInsideIndent(newLineCharOffset: Int, editor: Editor, dataContext: DataContext): Boolean =
    super.invokeInsideIndent(newLineCharOffset, editor, dataContext)

  override def preprocessEnter(
    file: PsiFile,
    editor: Editor,
    caretOffsetRef: Ref[Integer],
    caretAdvance: Ref[Integer],
    dataContext: DataContext,
    originalHandler: EditorActionHandler
  ): Result = {
    if (!file.is[ScalaFile])
      return Result.Continue

    if (!file.isIndentationBasedSyntaxSupported)
      return Result.Continue

    if (!CodeInsightSettings.getInstance.SMART_INDENT_ON_ENTER)
      return Result.Continue

    val caretOffset = caretOffsetRef.get.intValue

    val document = editor.getDocument

    val caretIsAtTheEndOfLine = isCaretAtTheEndOfLine(caretOffset, document)
    val result = if (caretIsAtTheEndOfLine) {
      // from [[com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.preprocessEnter]]:
      // Important Note: A document associated with the editor may have modifications which are not reflected yet in the PSI file.
      // If any operations with PSI are needed including a search for PSI elements, the document must be committed first to update the PSI.
      document.commit(editor.getProject)

      val elementAtCaret = ScalaEditorUtils.findElementAtCaret_WithFixedEOF(file, document, caretOffset)
      if (elementAtCaret == null)
        return Result.Continue

      val indentOptions = CodeStyle.getIndentOptions(file)
      val documentText = document.getCharsSequence
      val caretIndent = EnterHandlerUtils.calcCaretIndent(caretOffset, documentText, indentOptions.TAB_SIZE)
      val caretIndentSize = caretIndent.getOrElse(Int.MaxValue) // using MaxValue if the caret isn't inside code indent

      checkCaretAfterEmptyCaseClauseArrow(elementAtCaret, caretIndentSize, indentOptions) match {
        case Some(clause) =>
          // WORKAROUND:
          // press Enter after the case clause WITHOUT any code in the body
          // `case _ =><caret>` (with potential spaces around caret)
          insertNewLineWithSpacesAtCaret(editor, document, clause, indentOptions, extraSpaces = CodeStyle.getIndentSize(file), needRemoveTrailingSpaces = true)
          Result.Stop
        case _ =>
          val indentedElementOpt = ScalaIndentationSyntaxUtils.previousElementInIndentationContext(elementAtCaret, caretIndentSize, indentOptions)
          indentedElementOpt match {
            /** Incomplete block comments will be processed by [[com.intellij.codeInsight.editorActions.enter.EnterInBlockCommentHandler]] (SCL-21351) */
            case Some((indentedElement, _)) if !isIncompleteBlockComment(indentedElement, editor) =>
              insertNewLineWithSpacesAtCaret(editor, document, indentedElement, indentOptions, needRemoveTrailingSpaces = true)
              Result.Stop
            case _ =>
              Result.Continue
          }
      }
    }
    else {
      // looks like document commit is not required in this particular case
      val elementAtCaret = ScalaEditorUtils.findElementAtCaret_WithFixedEOF(file, document, caretOffset)
      if (elementAtCaret != null) {
        indentCodeToPreserveCorrectIndentationSyntax(document, elementAtCaret, caretOffset)
      }
      Result.Continue
    }
    //println(s"preprocessEnter: $result")
    result
  }
}

object Scala3IndentationBasedSyntaxEnterHandler {

  /**
   * @param elementAtCaret non-whitespace - if the caret located is in the end of document<br>
   *                       whitespace - otherwise
   * @example
   * input:{{{
   * def foo =
   *   42 //comment<caret>
   * }}}
   * output: {{{
   * 42
   * }}}

   * input:{{{
   * def foo =
   *   1; 2; 3; //comment<caret>
   * }}}
   * output: {{{
   * ;
   * }}}
   * NOTE: the semicolons are handled later for Scala 3
   */
  private[editor] def getLastRealElement(elementAtCaret: PsiElement): PsiElement = {
    val beforeWhitespace = elementAtCaret match {
      case ws: PsiWhiteSpace => PsiTreeUtil.prevLeaf(ws) match {
        case null =>
          return null // can be null when getLastRealElement is called during typing in "auto-braces" feature
        case prev => prev
      }
      case el => el
    }

    val withLineCommentSkipped = beforeWhitespace match {
      // for line comment we use prevCodeLeaf instead of prevSibling
      // because currently line comments are not attached to the line in indentation-based block
      case c: PsiComment if !c.startsFromNewLine() => PsiTreeUtil.prevCodeLeaf(c) match {
        case null => c
        case prev => prev
      }
      case el => el
    }
    withLineCommentSkipped
  }

  /**
   * There are multiple cases when we need to insert extra space before caret
   * to preserve the correct code with indentation-based syntax
   * =Example 1=
   * When the caret is just after case clause arrow `=>` and just before some code position {{{
   *   expr match
   *     case 42 => <caret>println("hello")
   * }}}
   * we need to insert an extra space before the code.
   * Otherwise Scala 3 parser will not parse the code as a child of the cause clause and it will not be indented:
   * {{{
   * BAD:
   * 1 match
   * case 2 =>
   * <CARET>3
   *
   * GOOD:
   * 1 match
   * case 2 =>
   * <CARET> 3
   * }}}
   *
   * ====Example 2====
   * (see SCL-20723)
   * When pressing enter just before `def` keyword: {{{
   *   extension (s: String) <caret>def foo: String = ???
   * }}}
   * we need it to transform into: {{{
   *   extension (s: String)
   *     <caret>def foo: String = ???
   * }}}
   */
  private def indentCodeToPreserveCorrectIndentationSyntax(
    document: Document,
    elementAtCaret: PsiElement,
    caretOffset: Int
  ): Unit =
    if (isCaretAfterCaseClauseArrowBeforeCode(elementAtCaret, caretOffset) ||
      isCaretBeforeOneLineExtensionDef(elementAtCaret)) {
      document.insertString(caretOffset, " ")
    }

  // `case _ =>   <caret>ref`
  private def isCaretAfterCaseClauseArrowBeforeCode(elementAtCaret: PsiElement, caretOffset: Int): Boolean = {
    val prevLeaf = PsiTreeUtil.prevCodeLeaf(elementAtCaret)
    prevLeaf match {
      case ElementType(ScalaTokenTypes.tFUNTYPE) & Parent(_: ScCaseClause) if caretOffset == elementAtCaret.startOffset =>
        true
      case _                                                                                                             =>
        false
    }
  }

  private def isCaretBeforeOneLineExtensionDef(elementAtCaret: PsiElement): Boolean = {
    val elementAtCaretAdjusted  = elementAtCaret.getParent match {
      case parent@ElementType(ScalaElementType.MODIFIERS) => parent.nextSiblingNotWhitespaceComment.getOrElse(elementAtCaret)
      case _ => elementAtCaret
    }
    elementAtCaretAdjusted match {
      case ElementType(ScalaTokenTypes.kDEF) & Parent(Parent(_: ScExtensionBody)) if !elementAtCaret.startsFromNewLine() =>
        true
      case _ => false
    }
  }

  /** @return Some(caseClause) if element before the caret represents a
   *         case clause without any code after the caret:
   *         {{{case _ =><caret><new line> (with optional spaces around caret)}}}
   */
  private def checkCaretAfterEmptyCaseClauseArrow(
    elementAtCaret: PsiElement,
    caretIndentSize: Int,
    indentOptions: IndentOptions,
  ): Option[ScCaseClause] = {
    val canBeAfterCaseClauseArrow =
      elementAtCaret match {
        // `case _ =><caret>EOF` (no whitespaces around caret, caret is at the end of file)
        // in this case element at caret represents empty case clause body
        case block: ScBlock   => block.getFirstChild == null
        case _: PsiWhiteSpace => true
        case _                => false
      }
    if (canBeAfterCaseClauseArrow) {
      val prevLeaf = PsiTreeUtil.prevLeaf(elementAtCaret) match {
        case b: ScBlock => PsiTreeUtil.prevLeaf(b)
        case el => el
      }
      prevLeaf match {
        case ElementType(ScalaTokenTypes.tFUNTYPE) & Parent(clause: ScCaseClause) =>
          val caretIsIndentedFromClause = ScalaIndentationSyntaxUtils.elementIndentSize(clause, caretIndentSize, indentOptions.TAB_SIZE).isDefined
          if (caretIsIndentedFromClause) Some(clause)
          else None
        case _ => None
      }
    }
    else None
  }

  private val SpaceOrTab = " \t"

  /** The logic is inspired by [[com.intellij.openapi.editor.actions.EnterAction.insertNewLineAtCaret]] */
  private def insertNewLineWithSpacesAtCaret(
    editor: Editor,
    document: Document,
    indentedElement: PsiElement,
    indentOptions: IndentOptions,
    needRemoveTrailingSpaces: Boolean = false,
    extraSpaces: Int = 0
  ): Unit = {
    val text = document.getCharsSequence
    val caretOffset = editor.getCaretModel.getOffset

    val prevIndentLineStartOffset = DocumentUtil.getLineStartOffset(indentedElement.startOffset, document)
    val prevIndentWsEndOffset = CharArrayUtil.shiftForward(text, prevIndentLineStartOffset, SpaceOrTab)
    // in case caret is placed before some element inside whitespace:
    // def foo =
    //    <caret>  42
    val prevIndentWsEndOffsetUntilCaret = prevIndentWsEndOffset.min(caretOffset)

    val spacesOnNewLine = text.subSequence(prevIndentLineStartOffset, prevIndentWsEndOffsetUntilCaret)
    val indentSize = IndentUtil.calcIndent(spacesOnNewLine, indentOptions.TAB_SIZE) + extraSpaces
    val indentString = new IndentInfo(1, indentSize, 0).generateNewWhiteSpace(indentOptions)

    document.insertString(caretOffset, indentString)
    val newCaretOffset = caretOffset + indentString.length
    editor.getCaretModel.moveToOffset(newCaretOffset)
    EditorModificationUtilEx.scrollToCaret(editor)
    editor.getSelectionModel.removeSelection()

    if (needRemoveTrailingSpaces) {
      removeTrailingSpaces(document, newCaretOffset)
    }
  }

  private def removeTrailingSpaces(document: Document, startOffset: Int): Unit = {
    val documentText = document.getCharsSequence

    val endOffset = CharArrayUtil.shiftForward(documentText, startOffset, SpaceOrTab)

    if (endOffset == documentText.length() || documentText.charAt(endOffset) == '\n') {
      document.deleteString(startOffset, endOffset)
    }
  }

  private def isCaretAtTheEndOfLine(caretOffset: Int, document: Document): Boolean = {
    val documentText = document.getCharsSequence
    val shifted = CharArrayUtil.shiftForward(documentText, caretOffset, SpaceOrTab)
    shifted == documentText.length || documentText.charAt(shifted) == '\n'
  }

  private def isIncompleteBlockComment(element: PsiElement, editor: Editor): Boolean = element match {
    case (comment: PsiComment) & ElementType(ScalaTokenTypes.tBLOCK_COMMENT) =>
      !ScalaIsCommentComplete.isCommentComplete(comment, ScalaCommenter, editor)
    case _ => false
  }
}
