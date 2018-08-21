package org.jetbrains.sbt.settings

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalSystemConfigurable
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.Context.Configuration
import org.jetbrains.sbt.project.settings._

/**
 * User: Dmitry Naydanov
 * Date: 11/25/13
 */
class SbtExternalSystemConfigurable(project: Project) 
  extends AbstractExternalSystemConfigurable[SbtProjectSettings, SbtProjectSettingsListener, SbtSettings](project, SbtProjectSystem.Id) {

  override def createProjectSettingsControl(settings: SbtProjectSettings): SbtProjectSettingsControl = new SbtProjectSettingsControl(Configuration, settings)

  override def createSystemSettingsControl(settings: SbtSettings): SbtSystemSettingsControl = new SbtSystemSettingsControl(settings)

  override def newProjectSettings(): SbtProjectSettings = new SbtProjectSettings()

  override def getId: String = "sbt.project.settings.configurable"
}
