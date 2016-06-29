package org.jetbrains.plugins.hocon.settings

import javax.swing.JComponent

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project

class HoconProjectSettingsConfigurable(project: Project) extends Configurable {
  private var panel = new HoconProjectSettingsPanel(project)

  def getDisplayName = "HOCON"

  def getHelpTopic = null

  def isModified: Boolean = panel.isModified

  def createComponent(): JComponent = panel.getMainComponent

  def disposeUIResources(): Unit = {
    panel = null
  }

  def apply(): Unit = panel.apply()

  def reset(): Unit = panel.loadSettings()
}
