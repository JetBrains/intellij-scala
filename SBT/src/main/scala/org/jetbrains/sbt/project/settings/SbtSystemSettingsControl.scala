package org.jetbrains.sbt
package project.settings

import com.intellij.openapi.externalSystem.util.{ExternalSystemSettingsControl, PaintAwarePanel}

/**
 * @author Pavel Fatin
 */
class SbtSystemSettingsControl(settings: SbtSettings) extends ExternalSystemSettingsControl[SbtSettings] {
  def isModified = false

  def showUi(show: Boolean) {}

  def fillUi(canvas: PaintAwarePanel, indentLevel: Int) {}

  def disposeUIResources() {}

  def apply(settings: SbtSettings) = null

  def reset() {}

  def validate(settings: SbtSettings) = true
}