package org.jetbrains.sbt.project.template

import java.io.File

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.project.{ModuleData, ProjectData}
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.settings.{AbstractExternalSystemSettings, ExternalSystemSettingsListener}
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil, ExternalSystemUtil}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import org.jetbrains.annotations.Nullable
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings

object SbtModuleBuilderUtil {

  def tryToSetupRootModel(model: ModifiableRootModel, @Nullable contentEntryPath: String, projectSettings: SbtProjectSettings): Boolean = {
    val attempt = for {
      contentPath <- Option(contentEntryPath)
      if contentPath.nonEmpty
      contentRootDir = new File(contentPath)
      if FileUtilRt.createDirectory(contentRootDir)
      vContentRootDir <- Option(LocalFileSystem.getInstance.refreshAndFindFileByIoFile(contentRootDir))
    } yield {
      doSetupRootModel(model, projectSettings, contentRootDir, vContentRootDir)
      true
    }

    attempt.getOrElse(false)
  }

  private def doSetupRootModel(model: ModifiableRootModel, externalProjectSettings: SbtProjectSettings,
                               contentRootDir: File, vContentRootDir: VirtualFile): Unit = {

    model.addContentEntry(vContentRootDir)
    model.inheritSdk()

    val module = model.getModule
    val rootPath = contentRootDir.getCanonicalPath

    // hack some dummy data so that external system realizes it can remove this module after sbt import
    // see com.intellij.openapi.externalSystem.service.project.manage.ModuleDataService.computeOrphanData
    val dummyModuleData = new ModuleData("N/A", SbtProjectSystem.Id, "N/A", module.getName, rootPath, rootPath)
    val dummyProjectData = new ProjectData(SbtProjectSystem.Id, module.getProject.getName, rootPath, rootPath)
    val esProperty = ExternalSystemModulePropertyManager.getInstance(module)
    esProperty.setExternalId(SbtProjectSystem.Id)
    esProperty.setExternalOptions(SbtProjectSystem.Id, dummyModuleData, dummyProjectData)

    val project = model.getProject
    val settings =
      ExternalSystemApiUtil.getSettings(project, SbtProjectSystem.Id)
        .asInstanceOf[
        AbstractExternalSystemSettings[
          _ <: AbstractExternalSystemSettings[_, SbtProjectSettings, _],
          SbtProjectSettings,
          _ <: ExternalSystemSettingsListener[SbtProjectSettings]]
        ]

    externalProjectSettings.setExternalProjectPath(contentRootDir.getAbsolutePath)
    settings.linkProject(externalProjectSettings)

    if (!externalProjectSettings.isUseAutoImport) {
      FileDocumentManager.getInstance.saveAllDocuments()
      ApplicationManager.getApplication.invokeLater(() => ExternalSystemUtil.refreshProjects(
        new ImportSpecBuilder(project, SbtProjectSystem.Id)
          .forceWhenUptodate()
          .use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
      ))
    }
  }

}
