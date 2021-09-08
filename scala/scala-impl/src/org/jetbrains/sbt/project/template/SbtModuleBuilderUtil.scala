package org.jetbrains.sbt.project.template

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.project.{ModuleData, ProjectData}
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil, ExternalSystemUtil}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.{ContentEntry, ModifiableRootModel}
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import org.jetbrains.annotations.Nullable
import org.jetbrains.jps.model.java.{JavaResourceRootType, JavaSourceRootType}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings

import java.io.File

object SbtModuleBuilderUtil {

  def tryToSetupRootModel(
    model: ModifiableRootModel,
    @Nullable contentEntryPath: String,
    contentEntryFolders: Option[DefaultModuleContentEntryFolders] = None
  ): Unit = {
    for {
      contentRootDir <- getOrCreateContentRootDir(contentEntryPath)
      vFile <- Option(LocalFileSystem.getInstance.refreshAndFindFileByIoFile(contentRootDir))
    } yield {
      doSetupRootModel(model, vFile, contentEntryFolders)
    }
  }

  def getOrCreateContentRootDir(contentEntryPath: String): Option[File] = {
    for {
      contentPath <- Option(contentEntryPath)
      if contentPath.nonEmpty
      contentRootDir = new File(contentPath)
      if FileUtilRt.createDirectory(contentRootDir)
    } yield {
      contentRootDir
    }
  }

  def doSetupModule(module: Module, externalProjectSettings: SbtProjectSettings, @Nullable contentEntryPath: String): Unit = {
    getOrCreateContentRootDir(contentEntryPath).foreach { contentRootDir =>
      val rootPath = contentRootDir.getCanonicalPath

      // hack some dummy data so that external system realizes it can remove this module after sbt import
      // see com.intellij.openapi.externalSystem.service.project.manage.ModuleDataService.computeOrphanData
      val dummyModuleData = new ModuleData("N/A", SbtProjectSystem.Id, "N/A", module.getName, rootPath, rootPath)
      val dummyProjectData = new ProjectData(SbtProjectSystem.Id, module.getProject.getName, rootPath, rootPath)
      val esProperty = ExternalSystemModulePropertyManager.getInstance(module)
      esProperty.setExternalId(SbtProjectSystem.Id)
      esProperty.setExternalOptions(SbtProjectSystem.Id, dummyModuleData, dummyProjectData)

      val project = module.getProject
      val settings =
        ExternalSystemApiUtil.getSettings(project, SbtProjectSystem.Id)
          .asInstanceOf[AbstractExternalSystemSettings[_, SbtProjectSettings, _]]

      externalProjectSettings.setExternalProjectPath(contentRootDir.getAbsolutePath)
      settings.linkProject(externalProjectSettings)

      FileDocumentManager.getInstance.saveAllDocuments()

      /** similar code is also called inside [[com.intellij.openapi.externalSystem.service.ExternalSystemStartupActivity.runActivity]]
       * In case the refresh below is not finished yet another refresh is cancelled in
       * `com.intellij.openapi.externalSystem.util.ExternalSystemUtil`*/
      invokeLater {
        ExternalProjectsManagerImpl.getInstance(project).init()
        ExternalSystemUtil.refreshProjects(
          new ImportSpecBuilder(project, SbtProjectSystem.Id)
            .use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
        )
      }
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
      vContentRootDir.toString + "/" + relativePath

    folders.sources.map(url).foreach(entry.addSourceFolder(_, JavaSourceRootType.SOURCE))
    folders.testSources.map(url).foreach(entry.addSourceFolder(_, JavaSourceRootType.TEST_SOURCE))
    folders.resources.map(url).foreach(entry.addSourceFolder(_, JavaResourceRootType.RESOURCE))
    folders.testResources.map(url).foreach(entry.addSourceFolder(_, JavaResourceRootType.TEST_RESOURCE))
    folders.excluded.map(url).foreach(entry.addExcludeFolder)
  }

  /**
   * Represents set of folders to be marked as "source" or "test" or "excluded" folders
   * after project is created and before sbt project reimport is finished.<br>
   * Otherwise we would need to wait for the project reimport finish even to create a simple scala file in sources folder.
   *
   * NOTE: all paths are relative to model content root
   */
  final case class DefaultModuleContentEntryFolders(
    sources: Seq[String],
    testSources: Seq[String],
    resources: Seq[String],
    testResources: Seq[String],
    excluded: Seq[String] = Seq("target", "project/target"),
  )
}
