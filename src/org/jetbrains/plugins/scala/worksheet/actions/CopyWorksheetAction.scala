package org.jetbrains.plugins.scala
package worksheet.actions

import java.awt.datatransfer.StringSelection

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.editor._
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetViewerInfo

/**
 * @author Ksenia.Sautina
 * @since 12/6/12
 */
class CopyWorksheetAction extends AnAction with TopComponentAction {
  def actionPerformed(e: AnActionEvent) {
    val editor = FileEditorManager.getInstance(e.getProject).getSelectedTextEditor
    if (editor == null) return

    val psiFile: PsiFile = PsiDocumentManager.getInstance(e.getProject).getPsiFile(editor.getDocument)
    val viewer = WorksheetViewerInfo.getViewer(editor)
    if (psiFile == null || viewer == null) return

    var s = mergeDocuments(editor, viewer)
    s = StringUtil.convertLineSeparators(s)
    val contents: StringSelection = new StringSelection(s)
    CopyPasteManager.getInstance.setContents(contents)
  }

  override def update(e: AnActionEvent) {
    val presentation = e.getPresentation
    presentation.setIcon(AllIcons.Actions.Copy)

    updateInner(presentation, e.getProject)
  }


  def mergeDocuments(editor: Editor, viewer: Editor): String =  {
    val leftDocument = editor.getDocument
    val rightDocument = viewer.getDocument
    val shift = 80
    val lineCountsMin = Math.min(leftDocument.getLineCount, rightDocument.getLineCount)
    val lineCountsMax = Math.max(leftDocument.getLineCount, rightDocument.getLineCount)
    val buffer = new StringBuilder()
    for (i <- 0 to lineCountsMin - 1) {
      val leftText = leftDocument.getText(new TextRange(leftDocument.getLineStartOffset(i), leftDocument.getLineEndOffset(i))).trim
      val rightText = rightDocument.getText(new TextRange(rightDocument.getLineStartOffset(i), rightDocument.getLineEndOffset(i))).trim
      val spaceCount = shift - leftText.length
      buffer.append(leftText)

      if (spaceCount > 0) {
        for (i  <- 1 to spaceCount) {
          buffer.append(" ")
        }
      } else {
        buffer.append("  ")
      }

      val prefix = if (rightText.startsWith(">")) "//" else "//|"
      if (rightText != "") buffer.append(prefix)
      buffer.append(rightText)
      if (i < lineCountsMin - 1) buffer.append("\n")
    }

    for (i <- lineCountsMin to lineCountsMax - 1) {
      if (leftDocument.getLineCount > rightDocument.getLineCount) {
        val leftText = leftDocument.getText(new TextRange(leftDocument.getLineStartOffset(i), leftDocument.getLineEndOffset(i)))
        buffer.append(leftText).append("\n")
      } else {
        val rightText = rightDocument.getText(new TextRange(rightDocument.getLineStartOffset(i), rightDocument.getLineEndOffset(i)))
          for (i  <- 1 to shift) {
            buffer.append(" ")
          }

        val prefix = if (rightText.startsWith(">")) "//" else "//|"
        if (rightText.trim != "")
          buffer.append(prefix).append(rightText).append("\n")
      }
    }

    buffer.toString()
  }

  override def actionIcon = AllIcons.Actions.Copy

  override def bundleKey = "worksheet.copy.button"
}
