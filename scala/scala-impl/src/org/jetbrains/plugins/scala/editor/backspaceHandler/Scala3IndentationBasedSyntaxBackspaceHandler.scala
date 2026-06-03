package org.jetbrains.plugins.scala.editor.backspaceHandler

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions._
import com.intellij.injected.editor.EditorWindow
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.{CaretModel, Document, Editor}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiFile, PsiWhiteSpace}
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.plugins.scala.Scala3Language
import org.jetbrains.plugins.scala.editor.backspaceHandler.Scala3IndentationBasedSyntaxBackspaceHandler._
import org.jetbrains.plugins.scala.editor.enterHandler.EnterHandlerUtils
import org.jetbrains.plugins.scala.editor.{ScalaEditorUtils, ScalaIndentationSyntaxUtils}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiFileExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClauses
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMatch

/**
 * There are also two default indenting backspace handlers:
 *  - [[SimpleIndentingBackspaceHandler]]
 *  - [[SmartIndentingBackspaceHandler]]
 *
 * They provide quite poor customization of how to handle backspace in indentation whitespace:<br>
 * you just need to by implement custom [[BackspaceModeOverride]].<br>
 * We do not use it because there are several issues with it:
 *  1. It's language-based.<br>
 *     Default handlers calculate the language based on the psi element at caret position
 *     (see [[BackspaceHandler.getLanguageAtCursorPosition]])<br>
 *     Unfortunately Scala 3 language detection is not reliable for non-file psi elements (see SCL-17237).
 *     (simply delegating to `getContainingFile.getLanguage` for every element seems to be too much.
 *  1. the logic of default handlers is quite primitive and you can't customize it to handle Scala3 indentation-based syntax
 *     in a more sophisticated way
 *
 * Also see [[org.jetbrains.plugins.scala.editor.enterHandler.Scala3IndentationBasedSyntaxEnterHandlerZ]]
 */
//noinspection InstanceOf
class Scala3IndentationBasedSyntaxBackspaceHandler extends BackspaceHandlerDelegate {

  private var myEnabled: Boolean = false

  //noinspection ConvertNullInitializerToUnderscore
  private var myReplacement: String = null
  private var myStartOffset: Int = 0

  override def beforeCharDeleted(c: Char, file: PsiFile, editor: Editor): Unit = {
    myEnabled = false

    val scalaFile = file match {
      case sf: ScalaFile => sf
      case _ => return
    }

    if (!scalaFile.isIndentationBasedSyntaxSupported)
      return

    val mode = getBackspaceMode(Scala3Language.INSTANCE)
    if (mode != SmartBackspaceMode.AUTOINDENT)
      return

    val caretModel = editor.getCaretModel
    val caretOffset = caretModel.getOffset
    val document = editor.getDocument
    val documentText = document.getCharsSequence

    val enable = doBeforeCharDeletedAutoindent(caretOffset, caretModel, documentText, file, editor, document)
    myEnabled = enable
  }

  /**
   * Most of the code is copied from [[SmartIndentingBackspaceHandler]]<br>
   * with some special handling for Scala3 indentation-based syntax
   */
  private def doBeforeCharDeletedAutoindent(
    caretOffset: Int,
    caretModel: CaretModel,
    documentText: CharSequence,
    file: PsiFile,
    editor: Editor,
    document: Document
  ): Boolean = {
    val pos = caretModel.getLogicalPosition
    val lineStartOffset = document.getLineStartOffset(pos.line)
    val beforeWhitespaceOffset = CharArrayUtil.shiftBackward(documentText, caretOffset - 1, SpaceOrTab) + 1

    val caretIsInsideIndentWhitespace = beforeWhitespaceOffset != lineStartOffset
    if (caretIsInsideIndentWhitespace) {
      myReplacement = null
      return false
    }

    val indentOptions = CodeStyle.getIndentOptions(file)
    val indentedElementIndentSize = findElementInIndentationContextOnPrevLine(caretOffset, documentText, file, indentOptions, lineStartOffset)

    /** NOTE: this is the main change comparing to [[SmartIndentingBackspaceHandler]] */
    myReplacement = indentedElementIndentSize match {
      case Some(elementIndentSize) =>
        StringUtil.repeatSymbol(' ', elementIndentSize) // TODO: handle TABS
      case None        =>
        CodeStyle.getLineIndent(editor, file.getLanguage, lineStartOffset, true)
    }
    if (myReplacement == null)
      return false

    val targetColumn = getWidth(myReplacement, indentOptions.TAB_SIZE)
    val endOffset = CharArrayUtil.shiftForward(documentText, caretOffset, SpaceOrTab)
      .min(documentText.length() - 1)
    // NOTE: extra check for `\n` to workaround IDEA-268212
    val logicalPosition =
      if (caretOffset < endOffset && documentText.charAt(endOffset) != '\n') editor.offsetToLogicalPosition(endOffset)
      else pos
    val currentColumn = logicalPosition.column

    if (currentColumn > targetColumn) {
      myStartOffset = lineStartOffset
    }
    else if (logicalPosition.line == 0) {
      myStartOffset = 0
      myReplacement = ""
    }
    else {
      val prevLineEndOffset = document.getLineEndOffset(logicalPosition.line - 1)
      myStartOffset = CharArrayUtil.shiftBackward(documentText, prevLineEndOffset - 1, SpaceOrTab) + 1
      if (myStartOffset != document.getLineStartOffset(logicalPosition.line - 1) || myStartOffset == 0) {
        var spacing = CodeStyle.getJoinedLinesSpacing(editor, file.getLanguage, endOffset, true)
        if (spacing < 0) {
          Log.error("The call `codeStyleFacade.getJoinedLinesSpacing` should not return the negative value")
          spacing = 0
        }
        myReplacement = StringUtil.repeatSymbol(' ', spacing)
      }
    }
    true
  }

  /** NOTE: the method copied without changes from SmartIndentingBackspaceHandler.doCharDeleted */
  override def charDeleted(c: Char, file: PsiFile, editor: Editor): Boolean = {
    if (!myEnabled)
      return false

    val document = editor.getDocument
    val caretModel = editor.getCaretModel
    val endOffset = CharArrayUtil.shiftForward(document.getImmutableCharSequence, caretModel.getOffset, SpaceOrTab)

    if (editor.isInstanceOf[EditorWindow]) {
      val ranges = InjectedLanguageManager.getInstance(file.getProject).intersectWithAllEditableFragments(file, new TextRange(myStartOffset, endOffset))
      if (ranges.size != 1 || !ranges.get(0).equalsToRange(myStartOffset, endOffset))
        return false
    }

    document.replaceString(myStartOffset, endOffset, myReplacement)
    caretModel.moveToOffset(myStartOffset + myReplacement.length)
    true
  }
}

object Scala3IndentationBasedSyntaxBackspaceHandler {

  private val Log = Logger.getInstance(classOf[Scala3IndentationBasedSyntaxBackspaceHandler])

  private val SpaceOrTab = " \t"

  /** copied from [[SmartIndentingBackspaceHandler.getWidth]] */
  private def getWidth(indent: String, tabSize: Int) = {
    var width = 0
    var idx = 0
    while (idx < indent.length) {
      val c = indent.charAt(idx)
      c match {
        case '\t' => width = tabSize * (width / tabSize + 1)
        case ' '  => width += 1
        case _    => Log.error("Unexpected whitespace character: " + c.toInt)
      }
      idx += 1
    }
    width
  }

  /** copied from [[AbstractIndentingBackspaceHandler.getBackspaceMode]] */
  //noinspection SameParameterValue
  private def getBackspaceMode(language: Language) = {
    var mode = CodeInsightSettings.getInstance.getBackspaceMode
    val overrider = LanguageBackspaceModeOverride.INSTANCE.forLanguage(language)
    if (overrider != null)
      mode = overrider.getBackspaceMode(mode)
    mode
  }

  private def findElementInIndentationContextOnPrevLine(
    caretOffset: Int,
    documentText: CharSequence,
    file: PsiFile,
    indentOptions: IndentOptions,
    lineStartOffset: Int
  ): Option[Int] = {
    val elementAtCaret0 = ScalaEditorUtils.findElementAtCaret_WithFixedEOF(file, documentText.length, caretOffset)
    val elementAtCaret = elementAtCaret0 match {
      case null              => null
      case ws: PsiWhiteSpace => ws
      case nonWs             => PsiTreeUtil.prevLeaf(nonWs)
    }
    if (elementAtCaret == null)
      return None

    val previousLineIsBlank = {
      // -2 = -1 (for new line) -1 (for prev whitespace)
      val prevLineBeforeWsOffset = CharArrayUtil.shiftBackward(documentText, lineStartOffset - 2, SpaceOrTab)
      prevLineBeforeWsOffset > 0 && {
        val ch = documentText.charAt(prevLineBeforeWsOffset)
        ch == '\n'
      }
    }

    // NOTE: If previous line is blank we always unindent caret,
    // and do not check if previous element is in an indented region
    // Exception is when the caret is in a whitespace between case clauses TODO: update this part of comment
    //
    // `caretOffset - 1` to skip latest indented element (if the caret is at the same indentation level with it)
    val searchFromOffset = if (!previousLineIsBlank)
      caretOffset
    else if (elementAtCaret.getParent.is[ScMatch, ScCaseClauses])
      caretOffset
    else {
      // if the caret is in the beginning of the line, we do not want to search the indented element from the previous line
      (caretOffset - 1).max(lineStartOffset)
    }

    val caretIndentSize = {
      val caretIndent = EnterHandlerUtils.calcCaretIndent(searchFromOffset, documentText, indentOptions.TAB_SIZE)
      caretIndent.getOrElse(Int.MaxValue) // using MaxValue if the caret isn't inside code indent// using MaxValue if the caret isn't inside code indent
    }
    val result = ScalaIndentationSyntaxUtils.previousElementInIndentationContext(elementAtCaret, caretIndentSize, indentOptions)
    result.map(_._2)
  }
}