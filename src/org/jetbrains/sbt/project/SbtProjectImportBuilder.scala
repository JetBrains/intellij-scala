package org.jetbrains.sbt
package project

import com.intellij.openapi.project.{ProjectManager, Project}
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import java.io.File
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsManager
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.externalSystem.service.settings.{ExternalSettingsControl, AbstractExternalProjectSettingsControl, AbstractImportFromExternalSystemControl}
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import settings._

/**
 * @author Pavel Fatin
 */
class SbtProjectImportBuilder(settingsManager: ExternalSystemSettingsManager, dataManager: ProjectDataManager)
  extends AbstractExternalProjectImportBuilder[SbtImportControl](settingsManager, dataManager, new SbtImportControl(), SbtProjectSystem.Id) {

  def getName = "SBT"

  def getIcon = SbtProjectSystem.Icon

  def doPrepare(context: WizardContext) {}

  def beforeCommit(dataNode: DataNode[ProjectData], project: Project) {}

  def onProjectInit(project: Project) {}

  def getExternalProjectConfigToUse(file: File): File = file

  def applyExtraSettings(context: WizardContext) {}
}

class SbtImportControl extends AbstractImportFromExternalSystemControl[SbtProjectSettings, SbtSettingsListener, SbtSettings](
  SbtProjectSystem.Id, new SbtSettings(ProjectManager.getInstance.getDefaultProject), new SbtProjectSettings()) {

  def getLinkedProjectChooserDescriptor = new FileChooserDescriptor(true, true, true, true, true, true)

  def onLinkedProjectPathChange(path: String) {}

  def createProjectSettingsControl(settings: SbtProjectSettings) = new AbstractExternalProjectSettingsControl[SbtProjectSettings](settings) {
    def isExtraSettingModified = false

    def applyExtraSettings(settings: SbtProjectSettings) = null

    def resetExtraSettings() {}

    def showExtraUi(show: Boolean) {}

    def disposeExtraUIControls() {}

    def fillExtraControls(content: PaintAwarePanel, indentLevel: Int) {}
  }

  def createSystemSettingsControl(settings: SbtSettings) = new ExternalSettingsControl[SbtSettings] {
    def isModified = false

    def showUi(show: Boolean) {}

    def fillUi(canvas: PaintAwarePanel, indentLevel: Int) {}

    def disposeUIResources() {}

    def apply(settings: SbtSettings) = null

    def reset() {}
  }
}
