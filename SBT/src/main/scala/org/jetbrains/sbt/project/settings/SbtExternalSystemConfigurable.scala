package org.jetbrains.sbt
package project.settings

import com.intellij.openapi.externalSystem.service.settings.{AbstractExternalProjectSettingsControl, AbstractExternalSystemConfigurable}
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.project.SbtProjectSystem

/**
 * User: Dmitry Naydanov
 * Date: 11/25/13
 */
class SbtExternalSystemConfigurable(project: Project) 
  extends AbstractExternalSystemConfigurable[SbtProjectSettings, SbtSettingsListener, SbtSettings](project, SbtProjectSystem.Id) {

  def createProjectSettingsControl(settings: SbtProjectSettings) = new SbtExternalProjectSettingsControl(settings)

  def createSystemSettingsControl(settings: SbtSettings) = null

  def newProjectSettings() = new SbtProjectSettings()

  def getId = "sbt.project.settings.configurable"

  def getHelpTopic: String = null
}

class SbtExternalProjectSettingsControl(settings: SbtProjectSettings)
        extends AbstractExternalProjectSettingsControl[SbtProjectSettings](settings) {

  def resetExtraSettings(isDefaultModuleCreation: Boolean) {}

  def applyExtraSettings(settings: SbtProjectSettings) {}

  def isExtraSettingModified = false

  def validate(settings: SbtProjectSettings) = true

  def fillExtraControls(content: PaintAwarePanel, indentLevel: Int) {}
}