package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.service.settings.AbstractImportFromExternalSystemControl
import org.jetbrains.sbt.project.settings._
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor

/**
 * @author Pavel Fatin
 */
class SbtImportControl extends AbstractImportFromExternalSystemControl[SbtProjectSettings, SbtSettingsListener, SbtSettings](
  SbtProjectSystem.Id, new SbtSettings(ProjectManager.getInstance.getDefaultProject), new SbtProjectSettings()) {

  def getLinkedProjectChooserDescriptor = new FileChooserDescriptor(true, true, true, true, true, true)

  def onLinkedProjectPathChange(path: String) {}

  def createProjectSettingsControl(settings: SbtProjectSettings) = new SbtProjectSettingsControl(Context.Wizard, settings)

  def createSystemSettingsControl(settings: SbtSettings) = new SbtSystemSettingsControl(settings)
}
