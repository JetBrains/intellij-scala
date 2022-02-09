package org.jetbrains.sbt.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.{AbstractOpenProjectProvider, ImportSpecBuilder}
import com.intellij.openapi.externalSystem.model.internal.InternalExternalProjectInfo
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectSystemId}
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.{ExternalProjectRefreshCallback, ProjectDataManager}
import com.intellij.openapi.externalSystem.service.ui.ExternalProjectDataSelectorDialog
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil, ExternalSystemUtil}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.settings.SbtSettings

import java.nio.file.Path

class SbtOpenProjectProvider() extends AbstractOpenProjectProvider {

  override def getSystemId: ProjectSystemId = SbtProjectSystem.Id

  override def isProjectFile(file: VirtualFile): Boolean =
    SbtProjectImportProvider.canImport(file)

  override def linkAndRefreshProject(projectDirectory: Path, project: Project): Unit = {
    val sbtProjectSettings = SbtProjectSettings.forProject(project).getOrElse(SbtProjectSettings.default)
    sbtProjectSettings.setExternalProjectPath(projectDirectory.toString)
    attachSbtProjectAndRefresh(sbtProjectSettings, project)
  }

  private def attachSbtProjectAndRefresh(settings: SbtProjectSettings, project: Project): Unit = {
    val externalProjectPath = settings.getExternalProjectPath
    ExternalSystemApiUtil.getSettings(project, SbtProjectSystem.Id)
      .asInstanceOf[SbtSettings]
      .linkProject(settings)
    ExternalSystemUtil.refreshProject(externalProjectPath,
      new ImportSpecBuilder(project, SbtProjectSystem.Id)
        .usePreviewMode()
        .use(ProgressExecutionMode.MODAL_SYNC))
    ExternalSystemUtil.refreshProject(externalProjectPath,
      new ImportSpecBuilder(project, SbtProjectSystem.Id)
        .callback(new FinalImportCallback(project, settings)))
  }


  // TODO duplicated with org.jetbrains.bsp.project.BspOpenProjectProvider.FinalImportCallback
  private class FinalImportCallback(project: Project, projectSettings: SbtProjectSettings)
    extends ExternalProjectRefreshCallback {

    override def onSuccess(externalProject: DataNode[ProjectData]): Unit = {

      if (externalProject == null) return

      def selectDataTask = {
        val projectInfo =
          new InternalExternalProjectInfo(SbtProjectSystem.Id, projectSettings.getExternalProjectPath, externalProject)
        val dialog = new ExternalProjectDataSelectorDialog(project, projectInfo)
        if (dialog.hasMultipleDataToSelect)
          dialog.showAndGet()
        else
          Disposer.dispose((dialog.getDisposable): Disposable)
      }

      def importTask(): Unit = {
        ProjectDataManager.getInstance().importData(externalProject, project, false)
      }

      val showSelectiveImportDialog = SbtSettings.getInstance(project).showSelectiveImportDialogOnInitialImport()
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
