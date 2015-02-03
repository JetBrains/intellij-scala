package org.jetbrains.plugins.scala
package util.macroDebug

import com.intellij.openapi.actionSystem._
import lang.psi.api.ScalaFile
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.icons.AllIcons
import extensions._
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import worksheet.runconfiguration.WorksheetViewerInfo
import java.awt.BorderLayout
import com.intellij.ui.JBSplitter
import com.intellij.openapi.fileEditor.FileEditorManager
import org.jetbrains.plugins.scala.worksheet.actions.{CleanWorksheetAction, TopComponentAction}
import com.intellij.openapi.editor.impl.EditorImpl
import javax.swing.DefaultBoundedRangeModel
import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.application.ApplicationManager

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
    val viewer =  WorksheetViewerInfo.getViewer(editor)
    
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
