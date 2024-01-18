package org.jetbrains.sbt.settings

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalSystemConfigurable
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.help.ScalaWebHelpProvider
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.Context.Configuration
import org.jetbrains.sbt.project.settings._

class SbtExternalSystemConfigurable(project: Project)
  extends AbstractExternalSystemConfigurable[SbtProjectSettings, SbtProjectSettingsListener, SbtSettings](project, SbtProjectSystem.Id) {

  override def createProjectSettingsControl(settings: SbtProjectSettings): SbtProjectSettingsControl = new SbtProjectSettingsControl(Configuration, settings)

  override def createSystemSettingsControl(settings: SbtSettings): SbtSettingsControl = new SbtSettingsControl(settings)

  override def newProjectSettings(): SbtProjectSettings = new SbtProjectSettings()

  override def getId: String = "sbt.project.settings.configurable"

  override def getHelpTopic: String =
    ScalaWebHelpProvider.HelpPrefix + "sbt_support.html"
}
