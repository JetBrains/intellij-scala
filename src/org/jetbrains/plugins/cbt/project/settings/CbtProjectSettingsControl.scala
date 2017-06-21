package org.jetbrains.plugins.cbt.project.settings

import javax.swing.JCheckBox

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil.getFillLineConstraints
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import org.jetbrains.sbt.project.settings.Context

class CbtProjectSettingsControl(context: Context, initialSettings: CbtProjectSettings)
  extends AbstractExternalProjectSettingsControl[CbtProjectSettings](initialSettings) {
  private val linkCbtLibsCheckBox = new JCheckBox("Link CBT libraries")

  override def applyExtraSettings(settings: CbtProjectSettings): Unit = {
    settings.linkCbtLibs = linkCbtLibsCheckBox.isSelected
  }

  override def resetExtraSettings(isDefaultModuleCreation: Boolean): Unit = {
    val initial = getInitialSettings
    linkCbtLibsCheckBox.setSelected(initial.linkCbtLibs)
  }

  override def fillExtraControls(content: PaintAwarePanel, indentLevel: Int): Unit = {
    val fillLineConstraints = getFillLineConstraints(indentLevel)

    content.add(linkCbtLibsCheckBox, fillLineConstraints)
  }

  override def isExtraSettingModified: Boolean = {
    val initial = getInitialSettings
    linkCbtLibsCheckBox.isSelected != initial.linkCbtLibs
  }

  override def validate(settings: CbtProjectSettings): Boolean = true
}
