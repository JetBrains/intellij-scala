package org.jetbrains.plugins.scala.worksheet.actions.topmenu

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.worksheet.WorksheetBundle
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterFactory

import java.awt.BorderLayout
import javax.swing.{DefaultBoundedRangeModel, Icon}

class CleanWorksheetAction extends AnAction(
  WorksheetBundle.message("clean.scala.worksheet.action.text"),
  WorksheetBundle.message("clean.scala.worksheet.action.description"),
  AllIcons.Actions.GC
) with TopComponentAction {

  override def genericText: String = WorksheetBundle.message("worksheet.clear.button")

  override def actionIcon: Icon = AllIcons.Actions.GC

  override def actionPerformed(e: AnActionEvent): Unit = {
    for { (editor, psiFile) <- getCurrentScalaWorksheetEditorAndFile(e) } {
      CleanWorksheetAction.cleanAll(editor, psiFile)
    }
  }
}

object CleanWorksheetAction {

  @TestOnly
  def cleanAll(editor: Editor, psiFile: PsiFile): Unit = {
    val project = psiFile.getProject
    val viewer = WorksheetCache.getInstance(project).getViewer(editor)

    if (psiFile == null || viewer == null) return

    val splitPane = viewer.getComponent.getParent
    if (splitPane == null) return
    val parent = splitPane.getParent
    if (parent == null && !ApplicationManager.getApplication.isUnitTestMode) return

    invokeLater {
      inWriteAction {
        resetScrollModel(viewer)
        WorksheetCache.getInstance(project).removePrinter(editor)
        cleanWorksheet(psiFile.getVirtualFile, viewer, project)

        if (!ApplicationManager.getApplication.isUnitTestMode) {
          parent.remove(splitPane)
          parent.add(editor.getComponent, BorderLayout.CENTER)
        }

        editor.getSettings.setFoldingOutlineShown(true)
        editor.getContentComponent.requestFocus() //  properly repaints editor SCL-16073
        DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
      }
    }
  }

  @RequiresEdt
  def resetScrollModel(viewer: Editor): Unit = {
    viewer match {
      case viewerEx: EditorImpl =>
        val scrollbar = viewerEx.getScrollPane.getVerticalScrollBar
        val commonModel = scrollbar.getModel
        val newModel = new DefaultBoundedRangeModel(
          commonModel.getValue, commonModel.getExtent, commonModel.getMinimum, commonModel.getMaximum
        )
        inWriteAction {
          scrollbar.setModel(newModel)
        }
      case _ =>
    }
  }

  @RequiresEdt
  def cleanWorksheet(file: VirtualFile, rightEditor: Editor, project: Project): Unit = {
    val rightDocument = rightEditor.getDocument

    WorksheetEditorPrinterFactory.deleteWorksheetEvaluation(file)

    if (rightDocument != null && !project.isDisposed) {
      inWriteAction {
        rightDocument.setText("")
        rightDocument.commit(project)
      }
    }
  }
}