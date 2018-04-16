package org.jetbrains.bsp

import java.io.File

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.wizard.{AbstractExternalProjectImportBuilder, AbstractExternalProjectImportProvider}
import com.intellij.openapi.externalSystem.service.settings.AbstractImportFromExternalSystemControl
import com.intellij.openapi.externalSystem.util.ExternalSystemSettingsControl
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.projectImport.ProjectOpenProcessorBase
import javax.swing.Icon
import org.jetbrains.sbt.project.SbtProjectImportBuilder

class BspProjectImportBuilder(projectDataManager: ProjectDataManager)
  extends AbstractExternalProjectImportBuilder[BspImportControl](projectDataManager, new BspImportControl(), bsp.ProjectSystemId) {

  override def doPrepare(context: WizardContext): Unit = {}
  override def beforeCommit(dataNode: DataNode[ProjectData], project: Project): Unit = {}
  override def getExternalProjectConfigToUse(file: File): File = file
  override def applyExtraSettings(context: WizardContext): Unit = {}
  override def getName: String = bsp.Name
  override def getIcon: Icon = bsp.Icon
}


class BspImportControl extends AbstractImportFromExternalSystemControl[BspProjectSettings, BspProjectSettingsListener, BspSystemSettings](
  bsp.ProjectSystemId, BspSystemSettings.getInstance(ProjectManager.getInstance.getDefaultProject), new BspProjectSettings) {

  override def onLinkedProjectPathChange(path: String): Unit = {}

  override def createProjectSettingsControl(settings: BspProjectSettings): ExternalSystemSettingsControl[BspProjectSettings] =
    new BspProjectSettingsControl(settings)

  override def createSystemSettingsControl(settings: BspSystemSettings): ExternalSystemSettingsControl[BspSystemSettings] =
    new BspSystemSettingsControl(settings)
}

class BspProjectImportProvider(builder: BspProjectImportBuilder)
  extends AbstractExternalProjectImportProvider(builder, bsp.ProjectSystemId)

class BspProjectOpenProcessor(builder: BspProjectImportBuilder) extends ProjectOpenProcessorBase[BspProjectImportBuilder](builder) {
  override def getSupportedExtensions: Array[String] = Array()
}