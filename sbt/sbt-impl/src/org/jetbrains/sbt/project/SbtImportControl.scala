package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.service.settings.AbstractImportFromExternalSystemControl
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NotNullFactory
import org.jetbrains.sbt.project.settings._
import org.jetbrains.sbt.settings.{SbtSettings, SbtSettingsControl}

class SbtImportControl extends AbstractImportFromExternalSystemControl[SbtProjectSettings, SbtProjectSettingsListener, SbtSettings](
  SbtProjectSystem.Id, SbtSettings.getInstance(ProjectManager.getInstance.getDefaultProject), SbtProjectSettings.default) {

  override def onLinkedProjectPathChange(path: String): Unit = {}

  override def createProjectSettingsControl(settings: SbtProjectSettings) = new SbtProjectSettingsControl(Context.Wizard, settings)

  override def createSystemSettingsControl(settings: SbtSettings) = new SbtSettingsControl(settings)
}

object SbtImportControl {
  object SbtImportControlFactory extends NotNullFactory[SbtImportControl] {
    override def create(): SbtImportControl = new SbtImportControl
  }
}