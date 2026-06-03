package org.jetbrains.plugins.scala.settings.sections

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaBundle

class WorksheetSettingsSectionConfigurable(project: Project) extends SettingsSectionConfigurable {
  override def getDisplayName: String = ScalaBundle.message("scala.project.settings.form.tabs.worksheet")
  override def createPanel(): SettingsSectionPanel = new WorksheetSettingsSectionPanel(project)
}
