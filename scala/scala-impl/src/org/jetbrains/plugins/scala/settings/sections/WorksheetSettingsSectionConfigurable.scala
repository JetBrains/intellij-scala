package org.jetbrains.plugins.scala.settings.sections

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.help.ScalaWebHelpProvider

class WorksheetSettingsSectionConfigurable(project: Project) extends SettingsSectionConfigurable {
  override def getDisplayName: String = ScalaBundle.message("scala.project.settings.form.tabs.worksheet")
  override def createPanel(): SettingsSectionPanel = new WorksheetSettingsSectionPanel(project)
  override def getHelpTopic: String = ScalaWebHelpProvider.HelpPrefix + "work-with-scala-worksheet-and-ammonite.html"
}
