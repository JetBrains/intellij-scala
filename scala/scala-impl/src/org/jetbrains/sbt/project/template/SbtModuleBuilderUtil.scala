package org.jetbrains.sbt.project.template

import java.io.File

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.project.{ModuleData, ProjectData}
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil, ExternalSystemUtil}
import com.intellij.openapi.module.{JavaModuleType, ModifiableModuleModel, Module, ModuleType}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings

object SbtModuleBuilderUtil {

  def tryToSetupRootModel(model: ModifiableRootModel, @Nullable contentEntryPath: String): Unit = {
    for {
      contentRootDir <- getOrCreateContentRootDir(contentEntryPath)
      vFile <- Option(LocalFileSystem.getInstance.refreshAndFindFileByIoFile(contentRootDir))
    } yield {
      doSetupRootModel(model, vFile)
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

  private def doSetupRootModel(model: ModifiableRootModel, vContentRootDir: VirtualFile): Unit = {
    model.addContentEntry(vContentRootDir)
    model.inheritSdk()
  }

}
