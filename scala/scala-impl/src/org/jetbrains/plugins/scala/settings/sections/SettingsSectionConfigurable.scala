package org.jetbrains.plugins.scala.settings.sections

import com.intellij.openapi.options.{Configurable, ConfigurationException}

import javax.swing.JComponent

abstract class SettingsSectionConfigurable extends Configurable {

  def createPanel(): SettingsSectionPanel

  private var currentPanel = Option.empty[SettingsSectionPanel]

  override def createComponent: JComponent = {
    val panel = currentPanel.getOrElse {
      val panel = createPanel()
      currentPanel = Some(panel)
      panel
    }
    panel.getRootPanel
  }

  override def isModified: Boolean = currentPanel.get.isModified
  @throws[ConfigurationException]
  override def apply(): Unit = currentPanel.get.apply()
  override def reset(): Unit = currentPanel.get.reset()
  override def disposeUIResources(): Unit = {
    currentPanel = None
  }
}
