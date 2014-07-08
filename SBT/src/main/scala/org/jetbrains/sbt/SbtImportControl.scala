package org.jetbrains.sbt

import com.intellij.openapi.externalSystem.service.settings.AbstractImportFromExternalSystemControl
import org.jetbrains.sbt.project.settings.{SbtSettings, SbtSettingsListener, SbtProjectSettings}
import org.jetbrains.sbt.project.{SbtSystemSettingsControl, SbtProjectSettingsControl, SbtProjectSystem}
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor

/**
 * @author Pavel Fatin
 */
class SbtImportControl extends AbstractImportFromExternalSystemControl[SbtProjectSettings, SbtSettingsListener, SbtSettings](
  SbtProjectSystem.Id, new SbtSettings(ProjectManager.getInstance.getDefaultProject), new SbtProjectSettings()) {

  def getLinkedProjectChooserDescriptor = new FileChooserDescriptor(true, true, true, true, true, true)

  def onLinkedProjectPathChange(path: String) {}

  def createProjectSettingsControl(settings: SbtProjectSettings) = new SbtProjectSettingsControl(settings)

  def createSystemSettingsControl(settings: SbtSettings) = new SbtSystemSettingsControl(settings)
}
