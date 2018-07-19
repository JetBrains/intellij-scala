package org.jetbrains.plugins.scala.worksheet.ui.dialog

import com.intellij.openapi.actionSystem.AnActionEvent

/**
  * User: Dmitry.Naydanov
  * Date: 17.07.18.
  */
class ShowRunTypeAdditionalSettingsButton(form: WorksheetSettingsSetForm) extends DedicatedSettingsButton("Show additional settings") {
  override def actionPerformed(anActionEvent: AnActionEvent): Unit = {
    form.getRunType.showAdditionalSettingsPanel().foreach(_())
  }
}
