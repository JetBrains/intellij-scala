package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.service.settings.AbstractImportFromExternalSystemControl
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.sbt.project.settings._
import org.jetbrains.sbt.settings.{SbtSystemSettings, SbtSystemSettingsControl}

/**
 * @author Pavel Fatin
 */
class SbtImportControl extends AbstractImportFromExternalSystemControl[SbtProjectSettings, SbtProjectSettingsListener, SbtSystemSettings](
  SbtProjectSystem.Id, SbtSystemSettings.getInstance(ProjectManager.getInstance.getDefaultProject), SbtProjectSettings.default) {

  def getLinkedProjectChooserDescriptor = new FileChooserDescriptor(true, true, true, true, true, true)

  def onLinkedProjectPathChange(path: String) {}

  def createProjectSettingsControl(settings: SbtProjectSettings) = new SbtProjectSettingsControl(Context.Wizard, settings)

  def createSystemSettingsControl(settings: SbtSystemSettings) = new SbtSystemSettingsControl(settings)
}
