package org.jetbrains.sbt.project

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.ui.EditorNotificationPanel
import org.jetbrains.sbt.SbtBundle

import javax.swing.JComponent
import javax.swing.event.HyperlinkEvent

private object SbtProjectImportStateNotificationPanel {
  def createNotificationPanel(project: Project, fileEditor: FileEditor): JComponent = {
    val panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning)
    panel.setText(SbtBundle.message("sbt.project.not.loaded.notification.panel.text"))
    val fixHandler = new EditorNotificationPanel.ActionHandler {
      override def handlePanelActionClick(panel: EditorNotificationPanel, event: HyperlinkEvent): Unit = {
        loadSbtProject()
      }

      override def handleQuickFixClick(editor: Editor, psiFile: PsiFile): Unit = {
        loadSbtProject()
      }

      private def loadSbtProject(): Unit = {
        SbtExternalSystemUtil.loadSbtProject(project)
      }
    }
    panel.createActionLabel(SbtBundle.message("sbt.project.not.loaded.notification.panel.action.label"), fixHandler, true)
    panel
  }
}
