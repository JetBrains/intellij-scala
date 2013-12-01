package org.jetbrains.sbt
package settings

import com.intellij.openapi.externalSystem.service.settings.{AbstractExternalProjectSettingsControl, AbstractExternalSystemConfigurable}
import org.jetbrains.sbt.project.settings.{ScalaSbtSettings, SbtSettingsListener, SbtProjectSettings => Settings}
import org.jetbrains.sbt.project.SbtProjectSystem
import com.intellij.openapi.externalSystem.util.{PaintAwarePanel, ExternalSystemSettingsControl}
import com.intellij.openapi.project.Project

/**
 * User: Dmitry Naydanov
 * Date: 11/25/13
 */
class SbtExternalSystemConfigurable(project: Project) 
  extends AbstractExternalSystemConfigurable[Settings, SbtSettingsListener, ScalaSbtSettings](project, SbtProjectSystem.Id) {
  def createProjectSettingsControl(settings: Settings): ExternalSystemSettingsControl[Settings] = {
    new AbstractExternalProjectSettingsControl[Settings](settings) {
      def resetExtraSettings(isDefaultModuleCreation: Boolean) {}

      def applyExtraSettings(settings: Settings) {}

      def isExtraSettingModified: Boolean = false

      def validate(settings: Settings): Boolean = true

      def fillExtraControls(content: PaintAwarePanel, indentLevel: Int) {}
    }
    
    
  }

  def createSystemSettingsControl(settings: ScalaSbtSettings): ExternalSystemSettingsControl[ScalaSbtSettings] = null

  def newProjectSettings(): Settings = new Settings

  def getId: String = "sbt.project.settings.configurable"

  def getHelpTopic: String = null
}
