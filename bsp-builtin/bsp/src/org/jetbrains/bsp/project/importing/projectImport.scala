package org.jetbrains.bsp.project.importing

import com.intellij.ide.impl.ProjectUtilKt
import com.intellij.ide.util.projectWizard.{ModuleWizardStep, WizardContext}
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.internal.InternalExternalProjectInfo
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, ExternalSystemDataKeys, ProjectSystemId}
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
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
import com.intellij.platform.eel.provider.EelProviderUtil
import com.intellij.projectImport.{ProjectImportBuilder, ProjectImportProvider, ProjectOpenProcessor}
import org.jetbrains.bsp.*
import org.jetbrains.bsp.project.importing.BspOpenProjectProvider.shouldGenerateBspConfig
import org.jetbrains.bsp.project.importing.BspProjectOpenProcessor.hasBspConfiguration
import org.jetbrains.bsp.project.importing.BspSetupConfigStep.BspConfigSetupTask
import org.jetbrains.bsp.project.importing.bspConfigSteps.*
import org.jetbrains.bsp.project.importing.experimental.GenerateBspConfig.GenerateBspConfigDialog
import org.jetbrains.bsp.project.importing.setup.{BspSetupProvider, NoConfigSetup}
import org.jetbrains.bsp.protocol.BspConnectionConfig
import org.jetbrains.bsp.settings.*
import org.jetbrains.bsp.settings.BspProjectSettings.*
import org.jetbrains.bsp.settings.PreImportConfig.*
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.project.external.SdkUtils
import org.jetbrains.sbt.project.{AbstractBuildToolOpenProjectProvider, SbtProjectImportProvider}

import java.nio.file.{Path, Paths}
import java.util
import java.util.Collections
import javax.swing.*
import kotlin.coroutines.Continuation

class BspProjectImportBuilder
  extends AbstractExternalProjectImportBuilder[BspImportControl](
    ProjectDataManager.getInstance(),
    BspImportControlFactory,
    BSP.ProjectSystemId) {
  private[importing] var externalBspWorkspace: Option[Path] = None
  private[importing] var preImportConfig: PreImportConfig = AutoPreImport
  private[importing] var serverConfig: BspServerConfig = AutoConfig
  /** Whether the Scala plugin generated the BSP connection file during initial import */
  private[importing] var bspConfigGenerated: Boolean = false

  /** The wizard system reuses the builder between different runs of the wizard (IDEA-246371),
   * so we need to manually reset on every run. On this occasion, we can preconfigure any
   * data that can be autodetected before running the wizard. */
  private[importing] def reset(): Unit = {
    preImportConfig = AutoPreImport
    serverConfig = AutoConfig
    bspConfigGenerated = false
  }

  private[importing] def autoConfigure(workspace: Path): Unit = {
    val configSetups = bspConfigSteps.configSetupChoices(workspace)
    if (configSetups.size == 1)
      BspJdkUtil.findOrCreateBestJdkForProject(None, EelProviderUtil.getEelDescriptor(workspace)).foreach(bspConfigSteps.configureBuilder(_, this, workspace, configSetups.head))
  }

  private def applyBspSetupSettings(project: Project): Unit = {
    val bspSettings = BspUtil.bspSettings(project)
    val projectSettings = bspSettings.getLinkedProjectSettings(getBspWorkspace.toString)
    projectSettings.preImportConfig = preImportConfig
    projectSettings.serverConfig = serverConfig
    projectSettings.bspConfigGenerated = bspConfigGenerated
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

  def setBspConfigGenerated(generated: Boolean): Unit =
    this.bspConfigGenerated = generated

  override def doPrepare(context: WizardContext): Unit = {}
  override def beforeCommit(dataNode: DataNode[ProjectData], project: Project): Unit = {}
  override def getExternalProjectConfigToUse(file: java.io.File): java.io.File = file
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
    project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, java.lang.Boolean.TRUE)

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
    val projectDirectory = getProjectDirectory(projectFile)
    new BspOpenProjectProvider().doLinkProject(projectDirectory, project)
  }

  /**
   * Same as `com.intellij.openapi.externalSystem.importing.AbstractOpenProjectProvider#getProjectDirectory`
   * but not a `suspend` function.
   */
  private def getProjectDirectory(file: VirtualFile): VirtualFile =
    if (file.isDirectory) file else file.getParent
}

//noinspection UnstableApiUsage
class BspOpenProjectProvider extends AbstractBuildToolOpenProjectProvider {

  override def getSystemId: ProjectSystemId = BSP.ProjectSystemId

  override def isProjectFile(file: VirtualFile): Boolean =
    canOpenProject(file)

  override def canOpenProject(file: VirtualFile): Boolean =
    BspProjectOpenProcessor.canOpenProject(file)

  override def doLinkProject(projectDirectory: VirtualFile, project: Project): Unit = {
    val bspProjectSettings = new BspProjectSettings()
    val workspace = projectDirectory.toNioPath
    bspProjectSettings.setExternalProjectPath(workspace.toString)
    attachBspProjectAndRefresh(bspProjectSettings, project, workspace)
  }

  private def attachBspProjectAndRefresh(settings: BspProjectSettings, project: Project, workspace: Path): Unit = {
    val externalProjectPath = settings.getExternalProjectPath
    BspUtil.bspSettings(project).linkProject(settings)
    ExternalSystemUtil.refreshProject(externalProjectPath,
      new ImportSpecBuilder(project, BSP.ProjectSystemId)
        .usePreviewMode()
        .use(ProgressExecutionMode.MODAL_SYNC))
    ExternalProjectsManagerImpl.getInstance(project).runWhenInitialized { () =>
      val setupChoices = bspConfigSteps.workspaceSetupChoices(workspace)
      if (shouldGenerateBspConfig(setupChoices, workspace, settings.serverConfig))
        generateBspConfig(workspace, setupChoices, project, settings)

      ExternalSystemUtil.refreshProject(
        externalProjectPath,
        new ImportSpecBuilder(project, BSP.ProjectSystemId)
          .withCallback(new FinalImportCallback(project, settings))
          .withImportProjectData(false)
      )
    }
  }

  private def generateBspConfig(
    workspace: Path,
    setupChoices: List[ConfigSetup],
    project: Project,
    settings: BspProjectSettings
  ): Unit = {
    val existingJdk = BspJdkUtil.findOrCreateBestJdkForProject(project)

    val (configSetupOpt, sdkOpt) =
      if (setupChoices.size > 1 || existingJdk.isEmpty)  {
        val dialog = new GenerateBspConfigDialog(setupChoices, project, existingJdk.isEmpty)
        if (dialog.showAndGet()) {
          val selectedJdk = dialog.getSelectedJdkIfRequired()
          selectedJdk.foreach(SdkUtils.addJdkIfNotExists)
          val sdk = existingJdk.orElse(selectedJdk)
          (Some(dialog.selectedConfigSetup), sdk)
        } else (None, None)
      } else {
        (setupChoices.headOption, existingJdk)
      }

    for {
      configSetup <- configSetupOpt
      sdk <- sdkOpt
    } {
      val params = bspConfigSteps.getBuilderConfigurationParameters(sdk, workspace, configSetup)
      val hasConnectionFile = params.serverConfig.exists(_.is[BspConfigFile])
      // If the project has no connection file, it means it will be generated
      // In practice, #generateBspConfig is only called when there are no existing connection files,
      // but let's keep this just in case.
      settings.bspConfigGenerated = !hasConnectionFile

      params.preImportConfig.foreach(settings.preImportConfig = _)
      params.serverConfig.foreach(settings.serverConfig = _)
      params.externalBspWorkspace.foreach(path => settings.setExternalProjectPath(path.toString))

      if (params.bspConfigSetup != NoConfigSetup) {
        val task = new BspConfigSetupTask(params.bspConfigSetup)
        task.queue()
      }
    }
  }

  // TODO duplicated with org.jetbrains.sbt.project.SbtOpenProjectProvider.FinalImportCallback
  private[importing] class FinalImportCallback(project: Project, projectSettings: BspProjectSettings)
  extends ExternalProjectRefreshCallback {

    override def onSuccess(externalProject: DataNode[ProjectData]): Unit = {
      if (externalProject == null || project.isDisposed)
        return

      def selectDataTask(): Unit = {
        if (project.isDisposed) return
        val projectInfo =
          new InternalExternalProjectInfo(BSP.ProjectSystemId, projectSettings.getExternalProjectPath, externalProject)
        val dialog = new ExternalProjectDataSelectorDialog(project, projectInfo)
        if (dialog.hasMultipleDataToSelect)
          dialog.showAndGet()
        else
          Disposer.dispose(dialog.getDisposable: Disposable)
      }

      def importTask(): Unit = {
        if (project.isDisposed) return
        ProjectDataManager.getInstance().importData(externalProject, project)
      }

      val showSelectiveImportDialog = BspSettings.getInstance(project).showSelectiveImportDialogOnInitialImport()
      val application = ApplicationManager.getApplication

      if (showSelectiveImportDialog && !application.isHeadlessEnvironment) {
        val runnable: Runnable = () => {
          selectDataTask()
          application.executeOnPooledThread {
            (() => importTask()): Runnable
          }
        }
        application.invokeLater(runnable, project.getDisposed)
      } else {
        importTask()
      }
    }
  }
}

private[importing] object BspOpenProjectProvider {
  /**
   * Determines whether the BSP connection file should be generated when a project is opened.
   *
   * @note When a project is imported with "New Project from Existing Sources" and multiple BSP import configs are available,
   * a selector is shown to choose one. Then, a modal blocking window is displayed to generate the connection file.
   * Later, when the project is opened, it should already have a connection file, so the connection file should not be
   * generated again.
   *
   * The only exception is the Bloop config. For this config type, the connection file is not generated
   * in a modal window because, with Bloop, we don't explicitly generate the connection file.
   * That is why a separate condition in this method checking whether the server config is not a [[BloopConfig]] is required.
   * If this condition is not present, the config selection dialog will be shown twice:
   * first during import via "New Project from Existing Sources", and then again when the project is opened.
   */
  def shouldGenerateBspConfig(setupChoices: List[ConfigSetup], workspace: Path, serverConfig: BspServerConfig): Boolean =
    setupChoices.nonEmpty && serverConfig != BloopConfig && !hasBspConfiguration(workspace)
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
    builder.autoConfigure(context.getProjectDirectory)
    builder.setFileToImport(context.getProjectDirectory.toString)
    Array(
      new BspSetupConfigStep(context, builder, context.getProjectDirectory),
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

  override def openProjectAsync(virtualFile: VirtualFile,
                                projectOpenOptions: ProjectOpenProcessor.ProjectOpenOptions,
                                continuation: Continuation[? >: Project]): AnyRef =
    new BspOpenProjectProvider().openProject(
      virtualFile,
      ProjectUtilKt.toOpenProjectTask(projectOpenOptions),
      continuation
    )
}

object BspProjectOpenProcessor {

  /**
   * Checks whether the given workspace directory belongs to the BSP external system.
   *
   * Any changes to the logic of this method should also be reflected in
   * [[org.jetbrains.bsp.project.BspUnlinkedProjectAware#isBspBuildFile]],
   * as both methods are responsible for determining whether a workspace (or a file within it) belongs to the BSP.
   */
  def canOpenProject(workspace: VirtualFile): Boolean = {
    val ioWorkspace = workspace.toNioPath

    // For projects inside Docker containers, it is not possible to use the "New from Existing Sources..." import method,
    // which is a known way to import sbt projects as BSP. To address this, importing sbt projects as BSP is enabled
    // when the project is simply opened, but only for Docker projects, so the user flow for local projects is not changed (SCL-17359).
    val eelDescriptor = EelProviderUtil.getEelDescriptor(ioWorkspace)
    // This workaround is needed because Docker eel descriptors are inside the Docker plugin, which we do not depend on.
    // If this hacky logic becomes problematic (a similar approach is used in `com.intellij.configurationStore.ProjectStoreImpl.getMachineWorkspacePath`),
    // it may be necessary to extract a separate module that depends on the Docker plugin,
    // checks the Eel descriptor there, and exposes an extension point.
    val name = eelDescriptor.getClass.getSimpleName
    val isDockerDescriptor = name == "DockerDevcontainerEelDescriptor" || name == "DockerContainerEelDescriptor"
    val canOpenSbtAsBspDocker =
      isDockerDescriptor && SbtProjectImportProvider.canImport(workspace)

    canOpenSbtAsBspDocker || hasBspConfiguration(ioWorkspace) || isScalaCliOrMill(ioWorkspace)
  }

  private[bsp] def hasBspConfiguration(workspace: Path): Boolean = {
    val bspConnectionProtocolSupported = BspConnectionConfig.workspaceConfigurationFiles(workspace).nonEmpty
    val bloopProject = BspUtil.bloopConfigDir(workspace).isDefined
    bspConnectionProtocolSupported || bloopProject
  }

  private[bsp] def isScalaCliOrMill(workspace: Path): Boolean =
    BspSetupProvider.canImport(workspace, MillSetup) || BspSetupProvider.canImport(workspace, ScalaCliSetup)
}
