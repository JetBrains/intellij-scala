package org.jetbrains.plugins.scala
package util.macroDebug

import java.awt.BorderLayout

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.ui.JBSplitter
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.worksheet.actions.{CleanWorksheetAction, TopComponentAction}
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache

/**
 * @author Ksenia.Sautina
 * @author Dmitry Naydanov        
 * @since 11/12/12
 */
class CleanMacrosheetAction() extends AnAction with TopComponentAction {

  def actionPerformed(e: AnActionEvent) {
    val editor: Editor = FileEditorManager.getInstance(e.getProject).getSelectedTextEditor
    val file: VirtualFile = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext)
    
    if (editor == null || file == null) return

    val psiFile: PsiFile = PsiDocumentManager.getInstance(e.getProject).getPsiFile(editor.getDocument)
    val viewer =  WorksheetCache.getInstance(e.getProject).getViewer(editor)
    
    if (psiFile == null || viewer == null) return

    val splitPane = viewer.getComponent.getParent.asInstanceOf[JBSplitter]
    val parent = splitPane.getParent
    if (parent == null) return
    
    invokeLater {
      inWriteAction {
        CleanWorksheetAction.resetScrollModel(viewer)
        
        CleanWorksheetAction.cleanWorksheet(psiFile.getNode, editor, viewer, e.getProject)

        parent.remove(splitPane)
        parent.add(editor.getComponent, BorderLayout.CENTER)
        editor.getSettings.setFoldingOutlineShown(true)
        ScalaMacroDebuggingUtil.macrosToExpand.clear()
      }
    }
  }

  override def update(e: AnActionEvent) {
    ScalaActionUtil.enableAndShowIfInScalaFile(e)
  }

  override def actionIcon = AllIcons.Actions.GC

  override def bundleKey = "worksheet.clear.button"
}
