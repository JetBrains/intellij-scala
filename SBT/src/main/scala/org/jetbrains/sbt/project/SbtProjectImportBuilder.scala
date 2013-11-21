package org.jetbrains.sbt
package project

import com.intellij.openapi.project.{ProjectManager, Project}
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import java.io.File
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.externalSystem.service.settings.{AbstractExternalProjectSettingsControl, AbstractImportFromExternalSystemControl}
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.openapi.externalSystem.util.ExternalSystemSettingsControl
import settings._
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager

/**
 * @author Pavel Fatin
 */
class SbtProjectImportBuilder(projectDataManager: ProjectDataManager)
  extends AbstractExternalProjectImportBuilder[SbtImportControl](projectDataManager, new SbtImportControl(), SbtProjectSystem.Id) {

  def getName = Sbt.Name

  def getIcon = Sbt.Icon

  def doPrepare(context: WizardContext) {}

  def beforeCommit(dataNode: DataNode[ProjectData], project: Project) {}

  def onProjectInit(project: Project) {}

  def getExternalProjectConfigToUse(file: File): File = file

  def applyExtraSettings(context: WizardContext) {}
}

class SbtImportControl extends AbstractImportFromExternalSystemControl[SbtProjectSettings, SbtSettingsListener, ScalaSbtSettings](
  SbtProjectSystem.Id, new ScalaSbtSettings(ProjectManager.getInstance.getDefaultProject), new SbtProjectSettings()) {

  def getLinkedProjectChooserDescriptor = new FileChooserDescriptor(true, true, true, true, true, true)

  def onLinkedProjectPathChange(path: String) {}

  def createProjectSettingsControl(settings: SbtProjectSettings) = new AbstractExternalProjectSettingsControl[SbtProjectSettings](settings) {
    def isExtraSettingModified = false

    def applyExtraSettings(settings: SbtProjectSettings) = null

    def resetExtraSettings(b: Boolean) {}

    def validate(settings: SbtProjectSettings): Boolean = true

    def fillExtraControls(content: PaintAwarePanel, indentLevel: Int) {}
  }

  def createSystemSettingsControl(settings: ScalaSbtSettings) = new ExternalSystemSettingsControl[ScalaSbtSettings] {
    def isModified = false

    def showUi(show: Boolean) {}

    def fillUi(canvas: PaintAwarePanel, indentLevel: Int) {}

    def disposeUIResources() {}

    def apply(settings: ScalaSbtSettings) = null

    def reset() {}

    def validate(settings: ScalaSbtSettings): Boolean = true
  }
}
