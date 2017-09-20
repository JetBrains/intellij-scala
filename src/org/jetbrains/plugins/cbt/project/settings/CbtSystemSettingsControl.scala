package org.jetbrains.plugins.cbt.project.settings

import com.intellij.openapi.externalSystem.util.{ExternalSystemSettingsControl, PaintAwarePanel}

class CbtSystemSettingsControl(settings: CbtSystemSettings)
  extends ExternalSystemSettingsControl[CbtSystemSettings] {

  def isModified: Boolean = false

  def showUi(show: Boolean): Unit = {}

  def fillUi(canvas: PaintAwarePanel, indentLevel: Int): Unit = {}

  def disposeUIResources() {}

  def apply(settings: CbtSystemSettings): Unit = {}

  def reset(): Unit = {}

  def validate(settings: CbtSystemSettings) = true
}