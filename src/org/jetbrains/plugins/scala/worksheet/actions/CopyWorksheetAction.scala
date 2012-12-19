package org.jetbrains.plugins.scala
package worksheet.actions

import com.intellij.openapi.actionSystem.{PlatformDataKeys, DataContext, AnActionEvent, AnAction}
import com.intellij.openapi.editor._
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import worksheet.runconfiguration.WorksheetViewerInfo
import java.lang.String
import java.awt.datatransfer.StringSelection
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil

/**
 * @author Ksenia.Sautina
 * @since 12/6/12
 */
class CopyWorksheetAction extends AnAction {
  def actionPerformed(e: AnActionEvent) {
    val dataContext: DataContext = e.getDataContext
    val editor: Editor = PlatformDataKeys.EDITOR.getData(dataContext)
    val project: Project = PlatformDataKeys.PROJECT.getData(dataContext)
    if (project == null || editor == null) return

    val file: PsiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)
    val viewer = WorksheetViewerInfo.getViewer(editor)
    if (file == null || viewer == null) return

    var s = mergeDocuments(editor, viewer)
    s = StringUtil.convertLineSeparators(s)
    val contents: StringSelection = new StringSelection(s)
    CopyPasteManager.getInstance.setContents(contents)
  }


  def mergeDocuments(editor: Editor, viewer: Editor): String =  {
    val leftDocument = editor.getDocument
    val rightDocument = viewer.getDocument
    val shift = 80
    val lineCountsMin = Math.min(leftDocument.getLineCount, rightDocument.getLineCount)
    val lineCountsMax = Math.max(leftDocument.getLineCount, rightDocument.getLineCount)
    val buffer = new StringBuilder()
    for (i <- 0 to lineCountsMin - 1) {
      val leftText = leftDocument.getText(new TextRange(leftDocument.getLineStartOffset(i), leftDocument.getLineEndOffset(i)))
      val rightText = rightDocument.getText(new TextRange(rightDocument.getLineStartOffset(i), rightDocument.getLineEndOffset(i)))
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
      if (rightText.trim != "") buffer.append(prefix)
      buffer.append(rightText).append("\n")
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

}
