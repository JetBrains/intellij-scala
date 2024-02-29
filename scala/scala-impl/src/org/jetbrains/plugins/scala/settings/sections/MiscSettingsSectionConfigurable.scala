package org.jetbrains.plugins.scala.settings.sections

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaBundle

final class MiscSettingsSectionConfigurable(project: Project) extends SettingsSectionConfigurable {
  override def getDisplayName: String = ScalaBundle.message("scala.project.settings.form.tabs.misc")
  override def createPanel(): SettingsSectionPanel = new MiscSettingsSectionPanel(project)
}
