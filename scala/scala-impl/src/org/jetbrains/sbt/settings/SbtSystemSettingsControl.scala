package org.jetbrains.sbt.settings

import com.intellij.openapi.externalSystem.util.{ExternalSystemSettingsControl, ExternalSystemUiUtil, PaintAwarePanel}

/**
 * @author Pavel Fatin
 */
class SbtSystemSettingsControl(settings: SbtSystemSettings) extends ExternalSystemSettingsControl[SbtSystemSettings] {

  private val pane = new SbtSettingsPane(settings.getProject)

  def isModified: Boolean = ! {
    val state = settings.getState
    pane.isCustomLauncher == state.customLauncherEnabled &&
      pane.getLauncherPath == state.customLauncherPath &&
      pane.getMaximumHeapSize == state.maximumHeapSize &&
      pane.getVmParameters == state.vmParameters &&
      pane.isCustomVM == state.customVMEnabled &&
      pane.getCustomVMPath == state.customVMPath
  }

  def showUi(show: Boolean): Unit =
    pane.getContentPanel.setVisible(show)

  def fillUi(canvas: PaintAwarePanel, indentLevel: Int): Unit =
    canvas.add(pane.getContentPanel, ExternalSystemUiUtil.getFillLineConstraints(indentLevel))

  def disposeUIResources() {}

  def apply(settings: SbtSystemSettings): Unit = {
    val state = settings.getState
    state.customLauncherEnabled = pane.isCustomLauncher
    state.customLauncherPath = pane.getLauncherPath
    state.maximumHeapSize = pane.getMaximumHeapSize
    state.vmParameters = pane.getVmParameters
    state.customVMEnabled = pane.isCustomVM
    state.customVMPath = pane.getCustomVMPath
  }

  def reset(): Unit = {
    val state = settings.getState
    pane.setCustomLauncherEnabled(state.customLauncherEnabled)
    pane.setLauncherPath(state.customLauncherPath)
    pane.setMaximumHeapSize(state.maximumHeapSize)
    pane.setMyVmParameters(state.vmParameters)
    pane.setCustomVMPath(state.customVMPath, state.getCustomVMEnabled)
    pane.setPathListeners()
  }

  def validate(settings: SbtSystemSettings) = true
}