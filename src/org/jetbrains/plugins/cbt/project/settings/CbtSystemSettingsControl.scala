package org.jetbrains.plugins.cbt.project.settings

import com.intellij.openapi.externalSystem.util.{ExternalSystemSettingsControl, ExternalSystemUiUtil, PaintAwarePanel}
import org.jetbrains.sbt.settings.SbtSettingsPane

class CbtSystemSettingsControl(settings: CbtSystemSettings)
  extends ExternalSystemSettingsControl[CbtSystemSettings] {

  private val pane = new CbtSettingsPane

  def isModified: Boolean =
    pane.cbtPath != settings.cbtPath

  def showUi(show: Boolean): Unit =
    pane.pane.setEnabled(true)

  def fillUi(canvas: PaintAwarePanel, indentLevel: Int): Unit =
    canvas.add(pane.pane, ExternalSystemUiUtil.getFillLineConstraints(indentLevel))

  def disposeUIResources() {}

  def apply(settings: CbtSystemSettings): Unit = {
    settings.cbtPath = pane.cbtPath
  }

  def reset(): Unit = {
    pane.updateCbtPath(settings.cbtPath)
  }

  def validate(settings: CbtSystemSettings) = true
}