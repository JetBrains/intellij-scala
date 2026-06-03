package org.jetbrains.plugins.scala.worksheet.actions.topmenu

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import org.jetbrains.plugins.scala.worksheet.WorksheetBundle
import org.jetbrains.plugins.scala.worksheet.settings.ui.WorksheetSettingsDialog

import javax.swing.Icon

class ShowWorksheetSettingsAction extends AnAction with TopComponentAction {

  override def genericText: String = WorksheetBundle.message("worksheet.settings.button")

  override def actionIcon: Icon = AllIcons.General.Settings

  override def actionPerformed(e: AnActionEvent): Unit = {
    for {(_, psiFile) <- getCurrentScalaWorksheetEditorAndFile(e)} {
      val dialog = new WorksheetSettingsDialog(psiFile)
      dialog.show()
    }
  }
}
