package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.service.settings.AbstractImportFromExternalSystemControl
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.sbt.project.settings._
import org.jetbrains.sbt.settings.{SbtSettings, SbtSettingsControl}

/**
 * @author Pavel Fatin
 */
class SbtImportControl extends AbstractImportFromExternalSystemControl[SbtProjectSettings, SbtProjectSettingsListener, SbtSettings](
  SbtProjectSystem.Id, SbtSettings.getInstance(ProjectManager.getInstance.getDefaultProject), SbtProjectSettings.default) {

  def getLinkedProjectChooserDescriptor = new FileChooserDescriptor(true, true, true, true, true, true)

  def onLinkedProjectPathChange(path: String) {}

  def createProjectSettingsControl(settings: SbtProjectSettings) = new SbtProjectSettingsControl(Context.Wizard, settings)

  def createSystemSettingsControl(settings: SbtSettings) = new SbtSettingsControl(settings)
}
