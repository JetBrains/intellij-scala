package org.jetbrains.bsp.project.importing

import com.intellij.ide.impl.ProjectUtilKt.runUnderModalProgressIfIsEdt
import com.intellij.ide.util.projectWizard.{ModuleWizardStep, WizardContext}
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.{AbstractOpenProjectProvider, ImportSpecBuilder}
import com.intellij.openapi.externalSystem.model.internal.InternalExternalProjectInfo
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectSystemId}
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.wizard.{AbstractExternalProjectImportBuilder, AbstractExternalProjectImportProvider}
import com.intellij.openapi.externalSystem.service.project.{ExternalProjectRefreshCallback, ProjectDataManager}
import com.intellij.openapi.externalSystem.service.settings.AbstractImportFromExternalSystemControl
import com.intellij.openapi.externalSystem.service.ui.ExternalProjectDataSelectorDialog
import com.intellij.openapi.externalSystem.util.{ExternalSystemSettingsControl, ExternalSystemUtil}
import com.intellij.openapi.module.{ModifiableModuleModel, Module}
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.{Disposer, NotNullFactory}
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.packaging.artifacts.ModifiableArtifactModel
import com.intellij.projectImport.{ProjectImportBuilder, ProjectImportProvider, ProjectOpenProcessor}
import org.jetbrains.bsp._
import org.jetbrains.bsp.protocol.BspConnectionConfig
import org.jetbrains.bsp.settings.BspProjectSettings._
import org.jetbrains.bsp.settings._
import org.jetbrains.plugins.scala.project.external.SdkUtils
import org.jetbrains.sbt.project.SbtProjectImportProvider

import java.io.File
import java.nio.file.{Path, Paths}
import java.util
import java.util.Collections
import javax.swing._
import scala.annotation.nowarn

class BspProjectImportBuilder
  extends AbstractExternalProjectImportBuilder[BspImportControl](
    ProjectDataManager.getInstance(),
    BspImportControlFactory,
    BSP.ProjectSystemId) {
  private[importing] var externalBspWorkspace: Option[Path] = None
  private[importing] var preImportConfig: PreImportConfig = AutoPreImport
  private[importing] var serverConfig: BspServerConfig = AutoConfig

  /** The wizard system reuses the builder between different runs of the wizard (IDEA-246371),
   * so we need to manually reset on every run. On this occasion, we can preconfigure any
   * data that can be autodetected before running the wizard. */
  private[importing] def reset(): Unit = {
    preImportConfig = AutoPreImport
    serverConfig = AutoConfig
  }

  private[importing] def autoConfigure(workspace: File): Unit = {
    val configSetups = bspConfigSteps.configSetupChoices(workspace)
    if (configSetups.size == 1)
      BspJdkUtil.getMostSuitableJdkForProject(None).foreach(bspConfigSteps.configureBuilder(_, this, workspace, configSetups.head))
  }

  private def applyBspSetupSettings(project: Project): Unit = {
    val bspSettings = BspUtil.bspSettings(project)
    val projectSettings = bspSettings.getLinkedProjectSettings(getBspWorkspace.toString)
    projectSettings.setPreImportConfig(preImportConfig)
    projectSettings.setServerConfig(serverConfig)
  }

  def setExternalBspWorkspace(str: Path): Unit = {
    this.externalBspWorkspace = Some(str)
  }

  def getBspWorkspace: Path = {
    externalBspWorkspace.getOrElse(Paths.get(getFileToImport))
  }

  def setPreImportConfig(preImportConfig: PreImportConfig): Unit =
    this.preImportConfig = preImportConfig

  def setServerConfig(bspConfig: BspServerConfig): Unit =
    this.serverConfig = bspConfig

  override def doPrepare(context: WizardContext): Unit = {}
  override def beforeCommit(dataNode: DataNode[ProjectData], project: Project): Unit = {}
  override def getExternalProjectConfigToUse(file: File): File = file
  override def applyExtraSettings(context: WizardContext): Unit = {}
  override def getName: String = BSP.Name
  override def getIcon: Icon = BSP.Icon

  override def setFileToImport(path: String): Unit = {
    if(externalBspWorkspace.isDefined) {
      super.setFileToImport(externalBspWorkspace.get.toString)
    } else {
      val localForImport = LocalFileSystem.getInstance()
      val file = localForImport.refreshAndFindFileByPath(path)

      Option(file).foreach { f =>
        val path = ProjectImportProvider.getDefaultPath(f)
        super.setFileToImport(path)
      }
    }
  }

  override def commit(project: Project,
                      model: ModifiableModuleModel,
                      modulesProvider: ModulesProvider,
                      artifactModel: ModifiableArtifactModel): util.List[Module] = {
    linkAndRefreshProject(getBspWorkspace.toString, project)
    applyBspSetupSettings(project)
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

  override def getSystemId: ProjectSystemId = BSP.ProjectSystemId

  override def isProjectFile(file: VirtualFile): Boolean =
    canOpenProject(file)

  override def canOpenProject(file: VirtualFile): Boolean =
    BspProjectOpenProcessor.canOpenProject(file)

  override def linkToExistingProject(projectFile: VirtualFile, project: Project): Unit = {
    val bspProjectSettings = new BspProjectSettings()
    val projectDirectory = getProjectDirectory(projectFile)
    bspProjectSettings.setExternalProjectPath(projectDirectory.toNioPath.toString)
    attachBspProjectAndRefresh(bspProjectSettings, project)
  }

  private def attachBspProjectAndRefresh(settings: BspProjectSettings, project: Project): Unit = {
    val externalProjectPath = settings.getExternalProjectPath
    BspUtil.bspSettings(project).linkProject(settings)
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
          Disposer.dispose(dialog.getDisposable: Disposable)
      }

      def importTask(): Unit = {
        ProjectDataManager.getInstance().importData(externalProject, project)
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
      SbtProjectImportProvider.canImport(fileOrDirectory) ||
      FastpassProjectImportProvider.canImport(fileOrDirectory)

  override def createSteps(context: WizardContext): Array[ModuleWizardStep] = {
    builder.reset()
    builder.autoConfigure(context.getProjectDirectory.toFile)
    builder.setFileToImport(context.getProjectDirectory.toString)
    Array(
      new BspSetupConfigStep(context, builder, context.getProjectDirectory.toFile),
      new BspChooseConfigStep(context, builder)
    )
  }

  override def getPathToBeImported(file: VirtualFile): String =
    ProjectImportProvider.getDefaultPath(file)
}

class BspProjectOpenProcessor extends ProjectOpenProcessor {

  override def getName: String = BSP.Name
  override def getIcon: Icon = BSP.Icon

  override def canOpenProject(file: VirtualFile): Boolean =
    BspProjectOpenProcessor.canOpenProject(file)

  override def doOpenProject(virtualFile: VirtualFile, projectToClose: Project, forceOpenInNewFrame: Boolean): Project =
    runUnderModalProgressIfIsEdt { (_, continuation) =>
      new BspOpenProjectProvider().openProject(virtualFile, projectToClose, forceOpenInNewFrame, continuation)
    }: @nowarn("cat=deprecation")
}

object BspProjectOpenProcessor {

  def canOpenProject(workspace: VirtualFile): Boolean = {
    val ioWorkspace = new File(workspace.getPath)

    val bspConnectionProtocolSupported = BspConnectionConfig.workspaceConfigurationFiles(ioWorkspace).nonEmpty
    val bloopProject = BspUtil.bloopConfigDir(ioWorkspace).isDefined
    // val sbtProject = SbtProjectImportProvider.canImport(workspace)
    // temporarily disable sbt importing via bloop from welcome screen (SCL-17359)
    val sbtProject = false

    val millProject = MillProjectImportProvider.canImport(workspace.toNioPath.toFile)

    bspConnectionProtocolSupported || bloopProject || bspConnectionProtocolSupported || sbtProject || millProject
  }
}
