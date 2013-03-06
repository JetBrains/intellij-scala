package org.jetbrains.plugins.scala
package worksheet.actions

import com.intellij.openapi.actionSystem._
import lang.psi.api.ScalaFile
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.openapi.util.TextRange
import com.intellij.icons.AllIcons
import com.intellij.lang.ASTNode
import extensions._
import com.intellij.openapi.vfs.VirtualFile
import worksheet.runconfiguration.WorksheetViewerInfo
import java.awt.BorderLayout
import com.intellij.ui.JBSplitter
import com.intellij.openapi.fileEditor.FileEditorManager

/**
 * @author Ksenia.Sautina
 * @since 11/12/12
 */
class CleanWorksheetAction() extends AnAction {

  def actionPerformed(e: AnActionEvent) {
    val editor: Editor = FileEditorManager.getInstance(e.getProject).getSelectedTextEditor
    val file: VirtualFile = PlatformDataKeys.VIRTUAL_FILE.getData(e.getDataContext)
    if (editor == null || file == null) return

    val psiFile: PsiFile = PsiDocumentManager.getInstance(e.getProject).getPsiFile(editor.getDocument)
    val viewer =  WorksheetViewerInfo.getViewer(editor)
    if (psiFile == null || viewer == null) return

    val splitPane = viewer.getComponent.getParent.asInstanceOf[JBSplitter]
    val parent = splitPane.getParent
    if (parent == null) return
    parent.remove(splitPane)
    parent.add(editor.getComponent, BorderLayout.CENTER)
    editor.getSettings.setFoldingOutlineShown(true)

    invokeLater {
      inWriteAction {
        val wvDocument = viewer.getDocument
        cleanWorksheet(psiFile.getNode, editor.getDocument, wvDocument, e.getProject)
      }
    }
  }

  override def update(e: AnActionEvent) {
    val presentation = e.getPresentation
    presentation.setIcon(AllIcons.Actions.GC)

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
        case sf: ScalaFile if (sf.isWorksheetFile) => enable()
        case _ => disable()
      }
    } catch {
      case e: Exception => disable()
    }
  }

  def cleanWorksheet(node: ASTNode, leftDocument: Document, rightDocument: Document, project: Project) {
    try {
      if (rightDocument != null && rightDocument.getLineCount > 0) {
        for (i <- rightDocument.getLineCount - 1 to 0 by -1) {
          val wStartOffset = rightDocument.getLineStartOffset(i)
          val wEndOffset = rightDocument.getLineEndOffset(i)

          val wCurrentLine = rightDocument.getText(new TextRange(wStartOffset, wEndOffset))
          if (wCurrentLine.trim != "" && wCurrentLine.trim != "\n" && i < leftDocument.getLineCount) {
            val eStartOffset = leftDocument.getLineStartOffset(i)
            val eEndOffset = leftDocument.getLineEndOffset(i)
            val eCurrentLine = leftDocument.getText(new TextRange(eStartOffset, eEndOffset))

            if ((eCurrentLine.trim == "" || eCurrentLine.trim == "\n") && eEndOffset + 1 < leftDocument.getTextLength) {
              leftDocument.deleteString(eStartOffset, eEndOffset + 1)
              PsiDocumentManager.getInstance(project).commitDocument(leftDocument)
            }
          }
        }
      }
    } finally {
      if (rightDocument != null && !project.isDisposed) {
        rightDocument.setText("")
        PsiDocumentManager.getInstance(project).commitDocument(rightDocument)
      }
    }
  }
}