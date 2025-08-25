package org.jetbrains.plugins.scala.project.template

import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.application.{ModalityState, ReadAction}
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.{ModuleData, ProjectData}
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.settings.{AbstractExternalSystemSettings, ExternalProjectSettings}
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil, ExternalSystemUtil}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ContentEntry, ModifiableRootModel}
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.{ApiStatus, Nullable}
import org.jetbrains.jps.model.java.{JavaResourceRootType, JavaSourceRootType}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.sbt.project.settings.SbtProjectSettings

import java.io.File

@ApiStatus.Internal
object ModuleBuilderUtil {

  @deprecated
  def doSetupModule(module: Module, externalProjectSettings: SbtProjectSettings, @Nullable contentEntryPath: String, projectSystemId: ProjectSystemId): Unit = {
    Option(contentEntryPath).foreach(tryToSetupModule(module, externalProjectSettings, _, projectSystemId))
  }

  def tryToSetupModule[T <: ExternalProjectSettings](module: Module, externalProjectSettings: T, contentEntryPath: String, projectSystemId: ProjectSystemId): Unit = {
    val dir = getOrCreateDir(contentEntryPath)
    dir.foreach(doSetupModule(module, externalProjectSettings, _, projectSystemId))
  }

  def doSetupModule[T <: ExternalProjectSettings](module: Module, externalProjectSettings: T, contentRootDir: File, projectSystemId: ProjectSystemId): Unit = {
    val rootPath = contentRootDir.getCanonicalPath

    // hack some dummy data so that external system realizes it can remove this module after sbt import
    // see com.intellij.openapi.externalSystem.service.project.manage.ModuleDataService.computeOrphanData
    val dummyModuleData = new ModuleData("N/A", projectSystemId, "N/A", module.getName, rootPath, rootPath)
    val dummyProjectData = new ProjectData(projectSystemId, module.getProject.getName, rootPath, rootPath)
    val esProperty = ExternalSystemModulePropertyManager.getInstance(module)
    esProperty.setExternalId(projectSystemId)
    esProperty.setExternalOptions(projectSystemId, dummyModuleData, dummyProjectData)

    val project = module.getProject
    val settings =
      ExternalSystemApiUtil.getSettings(project, projectSystemId)
        .asInstanceOf[AbstractExternalSystemSettings[_, T, _]]

    externalProjectSettings.setExternalProjectPath(contentRootDir.getAbsolutePath)
    settings.linkProject(externalProjectSettings)

    FileDocumentManager.getInstance.saveAllDocuments()

    StartupManager.getInstance(project).runAfterOpened { () =>
      /** similar code is also called inside [[com.intellij.openapi.externalSystem.service.ExternalSystemStartupActivity.runActivity]]
       * In case the refresh below is not finished yet another refresh is cancelled in
       * `com.intellij.openapi.externalSystem.util.ExternalSystemUtil`*/
      val manager = ExternalProjectsManagerImpl.getInstance(project)
      // 1. Set this setting explicitly, even though it is `false` by default (see com.intellij.openapi.project.ExternalStorageConfiguration)
      // In some rare cases default project settings `<idea_config>/options/default.project.xml`
      // contains `true` for `ExternalStorageConfiguration.enabled`
      // This can happen when IDEA settings were imported from previous old versions (in which the setting could be changed)
      // 2. Even though it's false by default, it's enabled explicitly in ExternalProjectsManagerImpl.setupCreatedProject so it's crucial to disable it again

      // IMPORTANT! #setStoreExternally should be called after the project is opened, because only then does WSM contain the entities that need to be migrated
      // from external to internal storage. See com.intellij.openapi.project.ExternalStorageConfigurationManagerImpl.updateEntitySource
      manager.setStoreExternally(false)
      manager.init()

      ExternalProjectsManagerImpl.getInstance(project).runWhenInitialized { () =>
        ExternalSystemUtil.refreshProjects(
          new ImportSpecBuilder(project, projectSystemId)
            .use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
        )
      }
    }
  }

  @deprecated
  def tryToSetupRootModel(
    model: ModifiableRootModel,
    @Nullable contentEntryPath: String,
    contentEntryFolders: Option[DefaultModuleContentEntryFolders] = None
  ): Unit = {
    Option(contentEntryPath).foreach(tryToSetupRootModel2(model, _, contentEntryFolders))
  }

  def tryToSetupRootModel2(
    model: ModifiableRootModel,
    contentEntryPath: String,
    contentEntryFolders: Option[DefaultModuleContentEntryFolders] = None
  ): Unit = {
    for {
      contentRootDir <- getOrCreateDir(contentEntryPath)
      vFile <- Option(LocalFileSystem.getInstance.refreshAndFindFileByIoFile(contentRootDir))
    } {
      doSetupRootModel(model, vFile, contentEntryFolders)
    }
  }

  /**
   * A three-step non-blocking read action sequence for opening files after project creation.
   *
   *   1. An action is registered to run after the new project is opened.
   *      After a behavioural change in the platform in 252, we are no longer allowed to interact with
   *      the PSI in any way inside this action.
   *      It seems that this causes a freeze in the UI.
   *   1. The first action kicks-off a non-blocking read action to query all `PsiFile` instances
   *      which correspond to the provided `VirtualFile` instances as an argument.
   *      The run action part runs on a background thread but scheduled as a separate computation.
   *      That way, the PSI access is outside the first action.
   *   1. Finally, the non-blocking read action finishes by scheduling a computation on the UI thread.
   *      Here, the queried `PsiFile` instances are opened in editors.
   */
  def openFilesInEditor(files: Seq[VirtualFile], project: Project): Unit = {
    if (files.isEmpty) return
    //noinspection ApiStatus
    StartupManager.getInstance(project).runAfterOpened(() => openFiles(files, project))
  }

  private def openFiles(files: Seq[VirtualFile], project: Project): Unit = {
    if (project.isDisposed) return

    ReadAction
      .nonBlocking[Array[PsiFile]](() => {
        val manager = PsiManager.getInstance(project)
        files.flatMap(vf => Option(manager.findFile(vf))).toArray
      })
      .expireWhen(() => project.isDisposed)
      .finishOnUiThread(ModalityState.nonModal(), EditorHelper.openFilesInEditor(_))
      .submit(AppExecutorUtil.getAppExecutorService)
  }

  private def doSetupRootModel(
    model: ModifiableRootModel,
    vContentRootDir: VirtualFile,
    contentEntryFolders: Option[DefaultModuleContentEntryFolders]
  ): Unit = {
    val entry: ContentEntry = model.addContentEntry(vContentRootDir)
    model.inheritSdk()

    contentEntryFolders.foreach(markDefaultModelContentEntryFolders(entry, vContentRootDir, _))
  }

  private def markDefaultModelContentEntryFolders(
    entry: ContentEntry,
    vContentRootDir: VirtualFile,
    folders: DefaultModuleContentEntryFolders,
  ): Unit = {
    def url(relativePath: String): String =
      vContentRootDir.toString + File.separator + relativePath

    folders.sources.map(url).foreach(entry.addSourceFolder(_, JavaSourceRootType.SOURCE))
    folders.testSources.map(url).foreach(entry.addSourceFolder(_, JavaSourceRootType.TEST_SOURCE))
    folders.resources.map(url).foreach(entry.addSourceFolder(_, JavaResourceRootType.RESOURCE))
    folders.testResources.map(url).foreach(entry.addSourceFolder(_, JavaResourceRootType.TEST_RESOURCE))
    folders.excluded.map(url).foreach(entry.addExcludeFolder)
  }

  private def getOrCreateDir(dirPath: String): Option[File] =
    if (dirPath.nonEmpty)
      Some(new File(dirPath)).filter(FileUtilRt.createDirectory)
    else
      None
}
