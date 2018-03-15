package org.jetbrains.sbt.settings

import com.intellij.openapi.externalSystem.util.{ExternalSystemSettingsControl, ExternalSystemUiUtil, PaintAwarePanel}

/**
 * @author Pavel Fatin
 */
class SbtSystemSettingsControl(settings: SbtSystemSettings) extends ExternalSystemSettingsControl[SbtSystemSettings] {

  private val pane = new SbtSettingsPane(settings.getProject)

  def isModified: Boolean = ! {
    pane.isCustomLauncher == settings.customLauncherEnabled &&
      pane.getLauncherPath == settings.customLauncherPath &&
      pane.getMaximumHeapSize == settings.maximumHeapSize &&
      pane.getVmParameters == settings.vmParameters &&
      pane.isCustomVM == settings.customVMEnabled &&
      pane.getCustomVMPath == settings.customVMPath
  }

  def showUi(show: Boolean): Unit =
    pane.getContentPanel.setVisible(show)

  def fillUi(canvas: PaintAwarePanel, indentLevel: Int): Unit =
    canvas.add(pane.getContentPanel, ExternalSystemUiUtil.getFillLineConstraints(indentLevel))

  def disposeUIResources() {}

  def apply(settings: SbtSystemSettings): Unit = {
    settings.customLauncherEnabled = pane.isCustomLauncher
    settings.customLauncherPath = pane.getLauncherPath
    settings.maximumHeapSize = pane.getMaximumHeapSize
    settings.vmParameters = pane.getVmParameters
    settings.customVMEnabled = pane.isCustomVM
    settings.customVMPath = pane.getCustomVMPath
  }

  def reset(): Unit = {
    pane.setCustomLauncherEnabled(settings.customLauncherEnabled)
    pane.setLauncherPath(settings.customLauncherPath)
    pane.setMaximumHeapSize(settings.maximumHeapSize)
    pane.setMyVmParameters(settings.vmParameters)
    pane.setCustomVMPath(settings.customVMPath, settings.getCustomVMEnabled)
    pane.setPathListeners()
  }

  def validate(settings: SbtSystemSettings) = true
}