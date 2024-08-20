package org.jetbrains.sbt.project.template

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
import com.intellij.openapi.roots.{ContentEntry, ModifiableRootModel}
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
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

    /** similar code is also called inside [[com.intellij.openapi.externalSystem.service.ExternalSystemStartupActivity.runActivity]]
     * In case the refresh below is not finished yet another refresh is cancelled in
     * `com.intellij.openapi.externalSystem.util.ExternalSystemUtil`*/
    invokeLater {
      val manager = ExternalProjectsManagerImpl.getInstance(project)
      // Set this setting explicitly, even though it is `false` by default (see com.intellij.openapi.project.ExternalStorageConfiguration)
      // In some rare cases default project settings `<idea_config>/options/default.project.xml`
      // contains `true` for `ExternalStorageConfiguration.enabled`
      // This can happen when IDEA settings were imported from previous old versions (in which the setting could be changed)
      manager.setStoreExternally(false)
      manager.init()
      ExternalSystemUtil.refreshProjects(
        new ImportSpecBuilder(project, projectSystemId)
          .use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
      )
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
