package org.jetbrains.plugins.scala.settings.sections

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.help.ScalaWebHelpProvider

class PerformanceSettingsSectionConfigurable(project: Project) extends SettingsSectionConfigurable {
  override def getDisplayName: String = ScalaBundle.message("scala.project.settings.form.tabs.performance")
  override def createPanel(): SettingsSectionPanel = new PerformanceSettingsSectionPanel(project)
  override def getHelpTopic: String = ScalaWebHelpProvider.HelpPrefix + "scala-improvingperformance.html"
}
