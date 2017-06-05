package org.jetbrains.plugins.cbt.project.settings

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import org.jetbrains.sbt.project.settings.Context

class CbtProjectSettingsControl(context: Context, initialSettings: CbtProjectSettings)
  extends AbstractExternalProjectSettingsControl[CbtProjectSettings](initialSettings) {

  override def applyExtraSettings(settings: CbtProjectSettings): Unit = {}

  override def resetExtraSettings(isDefaultModuleCreation: Boolean): Unit = {}

  override def fillExtraControls(content: PaintAwarePanel, indentLevel: Int): Unit = {}

  override def isExtraSettingModified: Boolean = false

  override def validate(settings: CbtProjectSettings): Boolean = true
}
