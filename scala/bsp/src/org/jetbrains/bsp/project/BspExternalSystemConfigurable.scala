package org.jetbrains.bsp.project

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalSystemConfigurable
import com.intellij.openapi.externalSystem.util.ExternalSystemSettingsControl
import com.intellij.openapi.project.Project
import org.jetbrains.bsp._

class BspExternalSystemConfigurable(project: Project)
  extends AbstractExternalSystemConfigurable[BspProjectSettings, BspProjectSettingsListener, BspSystemSettings](project, bsp.ProjectSystemId) {

  override def createProjectSettingsControl(settings: BspProjectSettings): ExternalSystemSettingsControl[BspProjectSettings] =
    new BspProjectSettingsControl(settings)

  override def createSystemSettingsControl(settings: BspSystemSettings): ExternalSystemSettingsControl[BspSystemSettings] =
    new BspSystemSettingsControl(settings)

  override def newProjectSettings(): BspProjectSettings = new BspProjectSettings

  override def getId: String = "bsp.project.settings.configurable"
}