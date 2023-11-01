package org.jetbrains.plugins.scala.editor.copy

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.{Editor, RawText}
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiFile, PsiWhiteSpace}
import org.jetbrains.plugins.scala.editor.ScalaEditorUtils
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

/**
 * This processor serves only one purpose: when the code is pasted right after `=` def/val/var it ensures that it's reformatted.
 * This "strange" logic is primarily needed for single scenario: move pasted multiline string literal to the new line.
 * It's done so because it will be anyway reformatted this way after explicit format.
 *
 * Example: {{{
 *   //Pasted code
 *   """1
 *     |2""".stripMargin
 *
 *   //Before
 *   def foo = <CARET>
 *
 *   //After
 *   def foo =
 *      """1
 *        |2""".stripMargin
 * }}}
 *
 * @note the way we do it now might be questionable: in reality we reformat any code pasted after `=`<br>
 *       It's the way it unintentionally behaved before and was convenient.
 *       If it causes any issues we can review the approach.
 */
final class ReformatCodeAfterDefinitionWithAssignCopyPastePreProcessor extends CopyPastePreProcessor {

  override def preprocessOnCopy(
    file: PsiFile,
    startOffsets: Array[Int],
    endOffsets: Array[Int],
    text: String
  ): String = null

  override def preprocessOnPaste(
    project: Project,
    file: PsiFile,
    editor: Editor,
    text: String,
    rawText: RawText
  ): String = {
    if (caretIsRightAfterAssignInDefinition(file, editor)) {
      //HACK: add extra whitespace in order IntelliJ enforces reformat for the code block
      // this logic is located in com.intellij.codeInsight.editorActions.PasteHandler.doPasteAction
      // (int indentOptions = pastedTextWasChanged ? CodeInsightSettings.REFORMAT_BLOCK : settings.REFORMAT_ON_PASTE;)
      " " + text
    }
    else text
  }

  private def caretIsRightAfterAssignInDefinition(
    file: PsiFile,
    editor: Editor,
  ): Boolean = {
    val caret = editor.getCaretModel.getCurrentCaret
    val selectionStart = caret.getSelectionStart
    val elementAtCaret = ScalaEditorUtils.findElementAtCaret_WithFixedEOF(file, editor.getDocument, selectionStart)

    val prev = elementAtCaret match {
      case null =>
        null
      case _: PsiWhiteSpace =>
        PsiTreeUtil.prevCodeLeaf(elementAtCaret)
      case other if other.getTextRange.getStartOffset == selectionStart =>
        PsiTreeUtil.prevCodeLeaf(other)
      case _ =>
        null
    }

    prev != null && prev.getNode.getElementType == ScalaTokenTypes.tASSIGN && prev.getParent.is[ScMember]
  }
}
