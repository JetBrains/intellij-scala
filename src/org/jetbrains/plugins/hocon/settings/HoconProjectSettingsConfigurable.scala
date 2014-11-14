package org.jetbrains.plugins.hocon.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project

class HoconProjectSettingsConfigurable(project: Project) extends Configurable {
  private var panel = new HoconProjectSettingsPanel(project)

  def getDisplayName = "HOCON"

  def getHelpTopic = null

  def isModified = panel.isModified

  def createComponent() = panel.getMainComponent

  def disposeUIResources(): Unit = {
    panel = null
  }

  def apply() = panel.apply()

  def reset() = panel.loadSettings()
}
