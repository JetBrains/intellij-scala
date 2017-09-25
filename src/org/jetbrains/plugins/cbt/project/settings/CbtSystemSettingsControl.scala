package org.jetbrains.plugins.cbt.project.settings

import com.intellij.openapi.externalSystem.util.{ExternalSystemSettingsControl, ExternalSystemUiUtil, PaintAwarePanel}
import org.jetbrains.sbt.settings.SbtSettingsPane

class CbtSystemSettingsControl(settings: CbtSystemSettings)
  extends ExternalSystemSettingsControl[CbtSystemSettings] {

  private val pane = new CbtSettingsPane

  def isModified: Boolean =
    pane.getCbtPath != settings.cbtPath

  def showUi(show: Boolean): Unit =
    pane.getPane.setEnabled(true)

  def fillUi(canvas: PaintAwarePanel, indentLevel: Int): Unit =
    canvas.add(pane.getPane, ExternalSystemUiUtil.getFillLineConstraints(indentLevel))

  def disposeUIResources() {}

  def apply(settings: CbtSystemSettings): Unit = {
    settings.cbtPath = pane.getCbtPath
  }

  def reset(): Unit = {
    pane.setCbtPath(settings.cbtPath)
  }

  def validate(settings: CbtSystemSettings) = true
}