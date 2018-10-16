package org.jetbrains.bsp.project

import java.io.File

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.wizard.{AbstractExternalProjectImportBuilder, AbstractExternalProjectImportProvider}
import com.intellij.openapi.externalSystem.service.settings.AbstractImportFromExternalSystemControl
import com.intellij.openapi.externalSystem.util.ExternalSystemSettingsControl
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessorBase
import javax.swing.Icon
import org.jetbrains.bsp._
import org.jetbrains.bsp.settings._

class BspProjectImportBuilder(projectDataManager: ProjectDataManager)
  extends AbstractExternalProjectImportBuilder[BspImportControl](projectDataManager, new BspImportControl(), BSP.ProjectSystemId) {

  override def doPrepare(context: WizardContext): Unit = {}
  override def beforeCommit(dataNode: DataNode[ProjectData], project: Project): Unit = {}
  override def getExternalProjectConfigToUse(file: File): File = file
  override def applyExtraSettings(context: WizardContext): Unit = {}
  override def getName: String = BSP.Name
  override def getIcon: Icon = BSP.Icon
}


class BspImportControl extends AbstractImportFromExternalSystemControl[BspProjectSettings, BspProjectSettingsListener, BspSettings](
  BSP.ProjectSystemId, BspSettings.getInstance(ProjectManager.getInstance.getDefaultProject), new BspProjectSettings) {

  override def onLinkedProjectPathChange(path: String): Unit = {}

  override def createProjectSettingsControl(settings: BspProjectSettings): ExternalSystemSettingsControl[BspProjectSettings] =
    new BspProjectSettingsControl(settings)

  override def createSystemSettingsControl(settings: BspSettings): ExternalSystemSettingsControl[BspSettings] =
    new BspSystemSettingsControl(settings)
}

class BspProjectImportProvider(builder: BspProjectImportBuilder)
  extends AbstractExternalProjectImportProvider(builder, BSP.ProjectSystemId) {

  override def canImport(fileOrDirectory: VirtualFile, project: Project): Boolean =
    BSP.enabled &&
    super.canImport(fileOrDirectory, project)
}

class BspProjectOpenProcessor(builder: BspProjectImportBuilder) extends ProjectOpenProcessorBase[BspProjectImportBuilder](builder) {
  override def getSupportedExtensions: Array[String] = Array()
}