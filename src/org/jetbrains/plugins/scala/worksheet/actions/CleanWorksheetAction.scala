package org.jetbrains.plugins.scala
package worksheet.actions

import com.intellij.openapi.actionSystem._
import lang.psi.api.ScalaFile
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiManager, PsiFile}
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.openapi.util.TextRange
import com.intellij.icons.AllIcons
import com.intellij.lang.ASTNode
import extensions._
import com.intellij.openapi.vfs.VirtualFile
import worksheet.runconfiguration.WorksheetViewerInfo
import java.awt.BorderLayout
import com.intellij.ui.JBSplitter

/**
 * @author Ksenia.Sautina
 * @since 11/12/12
 */
class CleanWorksheetAction() extends AnAction {

  def actionPerformed(e: AnActionEvent) {
    val dataContext: DataContext = e.getDataContext
    val editorFromContext: Editor = PlatformDataKeys.EDITOR.getData(dataContext)
    val project: Project = PlatformDataKeys.PROJECT.getData(dataContext)
    val file: VirtualFile = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext)
    if (project == null || editorFromContext == null || file == null) return
    val psiFile: PsiFile = (PsiManager.getInstance(project).asInstanceOf[PsiManagerEx]).getFileManager.getCachedPsiFile(file)
    if (psiFile == null) return
    val (editor, viewer) = if (editorFromContext.isViewer) {
      (WorksheetViewerInfo.findEditor(editorFromContext), editorFromContext)
    }  else {
      (editorFromContext, WorksheetViewerInfo.getViewer(editorFromContext))
    }
    if (editor == null || viewer == null) return

    val splitPane = viewer.getComponent.getParent.asInstanceOf[JBSplitter]
    val parent = splitPane.getParent
    if (parent == null) return
    parent.remove(splitPane)
    parent.add(editor.getComponent, BorderLayout.CENTER)
    editor.getSettings.setFoldingOutlineShown(true)

    invokeLater {
      inWriteAction {
        val wvDocument = viewer.getDocument
        cleanWorksheet(psiFile.getNode, editor.getDocument, wvDocument, project)
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
    enable()
    try {
      val file = LangDataKeys.PSI_FILE.getData(e.getDataContext)
      val editor: Editor = PlatformDataKeys.EDITOR.getData(e.getDataContext)

      if (file == null || editor == null)
        disable()

      file match {
        case sf: ScalaFile => {
          if (sf.isWorksheetFile) {
            enable()
          } else {
            disable()
          }
        }
        case _ => disable()
      }
    }
    catch {
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