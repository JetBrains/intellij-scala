package org.jetbrains.plugins.scala.settings.sections

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaBundle

final class UpdateSettingsSectionConfigurable(project: Project) extends SettingsSectionConfigurable {
  override def getDisplayName: String = ScalaBundle.message("scala.project.settings.form.tabs.updates")
  override def createPanel(): SettingsSectionPanel = new UpdateSettingsSectionPanel(project)
}
