package org.jetbrains.plugins.scala.worksheet.actions

import javax.swing.Icon

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import org.jetbrains.plugins.scala.worksheet.ui.dialog.WorksheetFileSettingsDialog

/**
  * User: Dmitry.Naydanov
  * Date: 06.02.18.
  */
class ShowWorksheetSettingsAction extends AnAction with TopComponentAction {
  override def actionPerformed(e: AnActionEvent): Unit = {
    ScalaActionUtil.getFileFrom(e).orElse(getSelectedFile(e.getProject)).foreach (
      new WorksheetFileSettingsDialog(_).show() 
    )
  }

  override def bundleKey: String = "worksheet.settings.button"

  override def actionIcon: Icon = AllIcons.General.Settings
}
