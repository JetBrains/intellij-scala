package org.jetbrains.plugins.scala.worksheet.actions.topmenu

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import javax.swing.Icon
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import org.jetbrains.plugins.scala.worksheet.ui.dialog.WorksheetFileSettingsDialog

class ShowWorksheetSettingsAction extends AnAction with TopComponentAction {

  override def genericText: String = ScalaBundle.message("worksheet.settings.button")

  override def actionIcon: Icon = AllIcons.General.Settings

  override def actionPerformed(e: AnActionEvent): Unit = {
    val fileOpt = ScalaActionUtil.getFileFrom(e).orElse(getSelectedFile(e.getProject))
    fileOpt.foreach(new WorksheetFileSettingsDialog(_).show())
  }
}
