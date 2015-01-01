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
  extends AbstractExternalSystemConfigurable[SbtProjectSettings, SbtProjectSettingsListener, SbtSystemSettings](project, SbtProjectSystem.Id) {

  def createProjectSettingsControl(settings: SbtProjectSettings) = new SbtProjectSettingsControl(Configuration, settings)

  def createSystemSettingsControl(settings: SbtSystemSettings) = new SbtSystemSettingsControl(settings)

  def newProjectSettings() = new SbtProjectSettings()

  def getId = "sbt.project.settings.configurable"

  def getHelpTopic = null
}
