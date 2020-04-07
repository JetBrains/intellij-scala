package org.jetbrains.bsp.project

import java.io.File
import java.util
import java.util.Collections

import com.intellij.ide.util.projectWizard.{ModuleWizardStep, WizardContext}
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.{AbstractOpenProjectProvider, ImportSpecBuilder}
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.internal.InternalExternalProjectInfo
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.service.project.wizard.{AbstractExternalProjectImportBuilder, AbstractExternalProjectImportProvider}
import com.intellij.openapi.externalSystem.service.project.{ExternalProjectRefreshCallback, ProjectDataManager}
import com.intellij.openapi.externalSystem.service.settings.AbstractImportFromExternalSystemControl
import com.intellij.openapi.externalSystem.service.ui.ExternalProjectDataSelectorDialog
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil, ExternalSystemSettingsControl, ExternalSystemUtil}
import com.intellij.openapi.module.{ModifiableModuleModel, Module}
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.{Disposer, NotNullFactory}
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.packaging.artifacts.ModifiableArtifactModel
import com.intellij.projectImport.{ProjectImportBuilder, ProjectImportProvider, ProjectOpenProcessor}
import javax.swing.Icon
import org.jetbrains.bsp._
import org.jetbrains.bsp.settings._
import org.jetbrains.sbt.project.SbtProjectImportProvider

class BspProjectImportBuilder
  extends AbstractExternalProjectImportBuilder[BspImportControl](
    ProjectDataManager.getInstance(),
    BspImportControlFactory,
    BSP.ProjectSystemId) {

  override def doPrepare(context: WizardContext): Unit = {}
  override def beforeCommit(dataNode: DataNode[ProjectData], project: Project): Unit = {}
  override def getExternalProjectConfigToUse(file: File): File = file
  override def applyExtraSettings(context: WizardContext): Unit = {}
  override def getName: String = BSP.Name
  override def getIcon: Icon = BSP.Icon

  override def setFileToImport(path: String): Unit = {
    val localForImport = LocalFileSystem.getInstance()
    val file = localForImport.refreshAndFindFileByPath(path)

    Option(file).foreach { f =>
      val path = ProjectImportProvider.getDefaultPath(f)
      super.setFileToImport(path)
    }
  }

  override def commit(project: Project,
                      model: ModifiableModuleModel,
                      modulesProvider: ModulesProvider,
                      artifactModel: ModifiableArtifactModel): util.List[Module] = {
    linkAndRefreshProject(getFileToImport, project)
    Collections.emptyList()
  }

  def linkAndRefreshProject(projectFilePath: String, project: Project): Unit = {
    val localFileSystem = LocalFileSystem.getInstance()
    val projectFile = localFileSystem.refreshAndFindFileByPath(projectFilePath)
    if (projectFile == null) {
      val shortPath = FileUtil.getLocationRelativeToUserHome(FileUtil.toSystemDependentName(projectFilePath), false)
      throw new IllegalArgumentException(s"project definition file $shortPath not found")
    }
    new BspOpenProjectProvider().linkToExistingProject(projectFile, project)
  }
}

class BspOpenProjectProvider() extends AbstractOpenProjectProvider {
  override def isProjectFile(file: VirtualFile): Boolean =
    canOpenProject(file)

  override def canOpenProject(file: VirtualFile): Boolean =
    BspProjectOpenProcessor.canOpenProject(file)

  override def linkAndRefreshProject(projectDirectory: String, project: Project): Unit = {
    val bspProjectSettings = new BspProjectSettings()
    bspProjectSettings.setExternalProjectPath(projectDirectory)
    attachBspProjectAndRefresh(bspProjectSettings, project)
  }

  private def attachBspProjectAndRefresh(settings: BspProjectSettings, project: Project): Unit = {
    val externalProjectPath = settings.getExternalProjectPath
    ExternalProjectsManagerImpl.getInstance(project).runWhenInitialized { () =>
      ExternalSystemUtil.ensureToolWindowInitialized(project, BSP.ProjectSystemId)
    }
    ExternalSystemApiUtil.getSettings(project, BSP.ProjectSystemId)
      .asInstanceOf[BspSettings]
      .linkProject(settings)
    ExternalSystemUtil.refreshProject(externalProjectPath,
      new ImportSpecBuilder(project, BSP.ProjectSystemId)
        .usePreviewMode()
        .use(ProgressExecutionMode.MODAL_SYNC))
    ExternalSystemUtil.refreshProject(externalProjectPath,
      new ImportSpecBuilder(project, BSP.ProjectSystemId)
        .callback(new FinalImportCallback(project, settings)))
  }

  // TODO duplicated with org.jetbrains.sbt.project.SbtOpenProjectProvider.FinalImportCallback
  private class FinalImportCallback(project: Project, projectSettings: BspProjectSettings)
  extends ExternalProjectRefreshCallback {

    override def onSuccess(externalProject: DataNode[ProjectData]): Unit = {

      if (externalProject == null) return

      def selectDataTask = {
        val projectInfo =
          new InternalExternalProjectInfo(BSP.ProjectSystemId, projectSettings.getExternalProjectPath, externalProject)
        val dialog = new ExternalProjectDataSelectorDialog(project, projectInfo)
        if (dialog.hasMultipleDataToSelect)
          dialog.showAndGet()
        else
          Disposer.dispose((dialog.getDisposable): Disposable)
      }

      def importTask(): Unit = {
        ProjectDataManager.getInstance().importData(externalProject, project, false)
      }

      val showSelectiveImportDialog = BspSettings.getInstance(project).showSelectiveImportDialogOnInitialImport()
      val application = ApplicationManager.getApplication

      if (showSelectiveImportDialog && !application.isHeadlessEnvironment) {
        application.invokeLater { () =>
          selectDataTask
          application.executeOnPooledThread { (() => importTask()): Runnable }
        }
      }
      else {
        importTask()
      }
    }
  }

}


class BspImportControl extends AbstractImportFromExternalSystemControl[BspProjectSettings, BspProjectSettingsListener, BspSettings](
  BSP.ProjectSystemId, BspSettings.getInstance(ProjectManager.getInstance.getDefaultProject), new BspProjectSettings) {

  override def onLinkedProjectPathChange(path: String): Unit = {}

  override def createProjectSettingsControl(settings: BspProjectSettings): ExternalSystemSettingsControl[BspProjectSettings] =
    new BspProjectSettingsControl(settings)

  override def createSystemSettingsControl(settings: BspSettings): ExternalSystemSettingsControl[BspSettings] =
    new BspSystemSettingsControl(settings)
}

object BspImportControlFactory extends NotNullFactory[BspImportControl] {
  override def create(): BspImportControl = new BspImportControl
}

class BspProjectImportProvider(builder: BspProjectImportBuilder)
  extends AbstractExternalProjectImportProvider(builder, BSP.ProjectSystemId) {

  def this() =
    this(ProjectImportBuilder.EXTENSIONS_POINT_NAME.findExtensionOrFail(classOf[BspProjectImportBuilder]))

  override def canImport(fileOrDirectory: VirtualFile, project: Project): Boolean =
    BspProjectOpenProcessor.canOpenProject(fileOrDirectory) ||
      SbtProjectImportProvider.canImport(fileOrDirectory)

  override def createSteps(context: WizardContext): Array[ModuleWizardStep] =
    ModuleWizardStep.EMPTY_ARRAY

  override def getPathToBeImported(file: VirtualFile): String =
    ProjectImportProvider.getDefaultPath(file)
}

class BspProjectOpenProcessor extends ProjectOpenProcessor {

  override def getName: String = BSP.Name
  override def getIcon: Icon = BSP.Icon

  override def canOpenProject(file: VirtualFile): Boolean =
    BspProjectOpenProcessor.canOpenProject(file)

  override def doOpenProject(virtualFile: VirtualFile, projectToClose: Project, forceOpenInNewFrame: Boolean): Project =
    new BspOpenProjectProvider().openProject(virtualFile, projectToClose, forceOpenInNewFrame)
}

object BspProjectOpenProcessor {

  def canOpenProject(workspace: VirtualFile): Boolean = {
    val ioWorkspace = new File(workspace.getPath)

    val bspConnectionProtocolSupported = BspUtil.bspConfigFiles(ioWorkspace).nonEmpty
    val bloopProject = BspUtil.bloopConfigDir(ioWorkspace).isDefined
    // val sbtProject = SbtProjectImportProvider.canImport(workspace)
    // temporarily disable sbt importing via bloop from welcome screen (SCL-17359)
    val sbtProject = false

    bspConnectionProtocolSupported || bloopProject || bspConnectionProtocolSupported || sbtProject
  }
}