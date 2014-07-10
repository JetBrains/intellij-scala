package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import org.jetbrains.annotations.NotNull
import org.jetbrains.sbt.project.settings.SbtProjectSettings

/**
 * @author Pavel Fatin
 */
class SbtProjectSettingsControl(settings: SbtProjectSettings) extends AbstractExternalProjectSettingsControl[SbtProjectSettings](settings) {
  def fillExtraControls(@NotNull paintAwarePanel: PaintAwarePanel, i: Int) {}
  def isExtraSettingModified: Boolean = false

  protected def resetExtraSettings(b: Boolean) {}

  protected def applyExtraSettings(sbtProjectSettings: SbtProjectSettings) {}

  def validate(sbtProjectSettings: SbtProjectSettings) = true
}