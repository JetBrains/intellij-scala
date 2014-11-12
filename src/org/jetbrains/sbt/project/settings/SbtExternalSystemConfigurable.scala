package org.jetbrains.sbt
package project.settings

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalSystemConfigurable
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.Context.Configuration

/**
 * User: Dmitry Naydanov
 * Date: 11/25/13
 */
class SbtExternalSystemConfigurable(project: Project) 
  extends AbstractExternalSystemConfigurable[SbtProjectSettings, SbtSettingsListener, SbtSettings](project, SbtProjectSystem.Id) {

  def createProjectSettingsControl(settings: SbtProjectSettings) = new SbtProjectSettingsControl(Configuration, settings)

  def createSystemSettingsControl(settings: SbtSettings) = new SbtSystemSettingsControl(settings)

  def newProjectSettings() = new SbtProjectSettings()

  def getId = "sbt.project.settings.configurable"

  def getHelpTopic = null
}
