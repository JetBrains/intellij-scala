package org.jetbrains.sbt
package settings

import com.intellij.openapi.options.Configurable

/**
 * @author Pavel Fatin
 */
class SbtConfigurable(settings: SbtApplicationSettings) extends Configurable {
  private lazy val pane = new SbtSettingsPane()

  def getDisplayName = "SBT"

  def getHelpTopic = null

  def createComponent() = pane.getContentPanel

  def isModified = ! {
    pane.isCustomLauncher == settings.customLauncherEnabled &&
    pane.getLauncherPath == settings.customLauncherPath &&
    pane.getMaximumHeapSize == settings.maximumHeapSize &&
    pane.getVmParameters == settings.vmParameters &&
    pane.isCustomVM == settings.customVMEnabled &&
    pane.getCustomVMPath == settings.customVMPath
  }

  def apply() {
    settings.customLauncherEnabled = pane.isCustomLauncher
    settings.customLauncherPath = pane.getLauncherPath
    settings.maximumHeapSize = pane.getMaximumHeapSize
    settings.vmParameters = pane.getVmParameters
    settings.customVMEnabled = pane.isCustomVM
    settings.customVMPath = pane.getCustomVMPath
  }

  def reset() {
    pane.setCustomLauncherEnabled(settings.customLauncherEnabled)
    pane.setLauncherPath(settings.customLauncherPath)
    pane.setMaximumHeapSize(settings.maximumHeapSize)
    pane.setMyVmParameters(settings.vmParameters)
    pane.setCustomVMEnabled(settings.customVMEnabled)
    pane.setCustomVMPath(settings.customVMPath)
  }

  def disposeUIResources() {}
}
