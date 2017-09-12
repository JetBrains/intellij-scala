package org.jetbrains.plugins.cbt.settings

import com.intellij.openapi.externalSystem.service.settings.{AbstractExternalProjectSettingsControl, AbstractExternalSystemConfigurable}
import com.intellij.openapi.externalSystem.util.{ExternalSystemSettingsControl, PaintAwarePanel}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.project.CbtProjectSystem
import org.jetbrains.plugins.cbt.project.settings.{CbtProjectSettings, CbtProjectSettingsListener, CbtSystemSettings}

class CbtExternalSystemConfigurable(project: Project)
  extends AbstractExternalSystemConfigurable[CbtProjectSettings, CbtProjectSettingsListener,
    CbtSystemSettings](project, CbtProjectSystem.Id) {

  override def newProjectSettings(): CbtProjectSettings = new CbtProjectSettings

  override def createSystemSettingsControl(settings: CbtSystemSettings): ExternalSystemSettingsControl[CbtSystemSettings] =
    new ExternalSystemSettingsControl[CbtSystemSettings] {
      override def apply(settings: CbtSystemSettings): Unit = {}

      override def isModified: Boolean = false

      override def reset(): Unit = {}

      override def showUi(show: Boolean): Unit = {}

      override def disposeUIResources(): Unit = {}

      override def validate(settings: CbtSystemSettings): Boolean = true

      override def fillUi(canvas: PaintAwarePanel, indentLevel: Int): Unit = {}
    }

  override def createProjectSettingsControl(settings: CbtProjectSettings): AbstractExternalProjectSettingsControl[CbtProjectSettings] =
    new AbstractExternalProjectSettingsControl[CbtProjectSettings](settings) {
      override def applyExtraSettings(settings: CbtProjectSettings): Unit = {}

      override def resetExtraSettings(isDefaultModuleCreation: Boolean): Unit = {}

      override def fillExtraControls(content: PaintAwarePanel, indentLevel: Int): Unit = {}

      override def isExtraSettingModified: Boolean = false

      override def validate(settings: CbtProjectSettings): Boolean = true
    }


  override def getId: String = "cbt.project.settings.configurable"
}
