package org.jetbrains.plugins.cbt.project.settings

import javax.swing.JCheckBox

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil.getFillLineConstraints
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import org.jetbrains.sbt.project.settings.Context

class CbtProjectSettingsControl(context: Context, initialSettings: CbtProjectSettings)
  extends AbstractExternalProjectSettingsControl[CbtProjectSettings](initialSettings) {
  private val isCbtCheckBox = new JCheckBox("Do not link CBT (use when opening CBT itself)")

  override def applyExtraSettings(settings: CbtProjectSettings): Unit = {
    settings.isCbt = isCbtCheckBox.isSelected
  }

  override def resetExtraSettings(isDefaultModuleCreation: Boolean): Unit = {
    val initial = getInitialSettings
    isCbtCheckBox.setSelected(initial.isCbt)
  }

  override def fillExtraControls(content: PaintAwarePanel, indentLevel: Int): Unit = {
    val fillLineConstraints = getFillLineConstraints(indentLevel)

    content.add(isCbtCheckBox, fillLineConstraints)
  }

  override def isExtraSettingModified: Boolean = {
    val initial = getInitialSettings
    isCbtCheckBox.isSelected != initial.isCbt
  }

  override def validate(settings: CbtProjectSettings): Boolean = true
}
