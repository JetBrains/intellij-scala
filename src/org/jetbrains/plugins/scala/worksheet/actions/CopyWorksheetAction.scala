package org.jetbrains.plugins.scala
package worksheet.actions

import com.intellij.openapi.actionSystem.{AnActionEvent, AnAction}
import com.intellij.openapi.editor._
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import worksheet.runconfiguration.WorksheetViewerInfo
import java.lang.String
import java.awt.datatransfer.StringSelection
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManager
import lang.psi.api.ScalaFile
import scala.Predef
import javax.swing.Icon

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

    def enable() {
      presentation.setEnabled(true)
      presentation.setVisible(true)
    }
    def disable() {
      presentation.setEnabled(false)
      presentation.setVisible(false)
    }

    try {
      val editor = FileEditorManager.getInstance(e.getProject).getSelectedTextEditor
      val psiFile: PsiFile = PsiDocumentManager.getInstance(e.getProject).getPsiFile(editor.getDocument)
      psiFile match {
        case sf: ScalaFile if sf.isWorksheetFile => enable()
        case _ => disable()
      }
    } catch {
      case e: Exception => disable()
    }
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
