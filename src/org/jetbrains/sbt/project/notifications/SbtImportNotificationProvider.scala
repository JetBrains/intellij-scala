package org.jetbrains.sbt
package project.notifications

import java.util.Collections

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.{ExternalProjectRefreshCallback, ProjectDataManager}
import com.intellij.openapi.externalSystem.util.{DisposeAwareProjectChange, ExternalSystemApiUtil, ExternalSystemUtil}
import com.intellij.openapi.fileEditor._
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.{EditorNotificationPanel, EditorNotifications}
import com.intellij.util.containers.ContainerUtilRt
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.settings.SbtSystemSettings

import scala.collection.mutable

/**
 * @author Nikolay Obedin
 * @since 3/24/15.
 */

abstract class SbtImportNotificationProvider(project: Project, notifications: EditorNotifications)
    extends EditorNotifications.Provider[EditorNotificationPanel] {

  private val ignoredFiles = mutable.Set.empty[VirtualFile]

  def shouldShowPanel(file: VirtualFile, fileEditor: FileEditor): Boolean

  def createPanel(file: VirtualFile): EditorNotificationPanel

  override def createNotificationPanel(file: VirtualFile, fileEditor: FileEditor): EditorNotificationPanel =
    if (!isIgnored(file) && isSbtFile(file) && shouldShowPanel(file, fileEditor)) createPanel(file) else null

  protected def refreshProject(): Unit = {
    FileDocumentManager.getInstance.saveAllDocuments()
    ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, SbtProjectSystem.Id).forceWhenUptodate(true))
  }

  protected def importProject(file: VirtualFile): Unit = {
    val externalProjectPath = {
      if (file.getName == Sbt.BuildFile)
        file.getParent.getCanonicalPath
      else
        file.getParent.getParent.getCanonicalPath
    }

    val projectSettings = SbtProjectSettings.default
    projectSettings.setUseOurOwnAutoImport(true)
    projectSettings.setExternalProjectPath(externalProjectPath)

    val callback = new ExternalProjectRefreshCallback() {
      def onFailure(errorMessage: String, errorDetails: String) {}

      def onSuccess(externalProject: DataNode[ProjectData]) {
        if (externalProject == null) return

        val sbtSystemSettings = SbtSystemSettings.getInstance(project)

        val projects = ContainerUtilRt.newHashSet(sbtSystemSettings.getLinkedProjectsSettings)
        projects.add(projectSettings)
        sbtSystemSettings.setLinkedProjectsSettings(projects)

        ExternalSystemApiUtil.executeProjectChangeAction(new DisposeAwareProjectChange(project) {
          def execute() {
            ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring(new Runnable {
              def run() {
                val dataManager: ProjectDataManager = ServiceManager.getService(classOf[ProjectDataManager])
                dataManager.importData[ProjectData](Collections.singleton(externalProject), project, false)
              }
            })
          }
        })
      }
    }

    FileDocumentManager.getInstance.saveAllDocuments()
    ExternalSystemUtil.refreshProject(project,
      SbtProjectSystem.Id, projectSettings.getExternalProjectPath, callback,
      false, ProgressExecutionMode.IN_BACKGROUND_ASYNC)
  }

  protected def getExternalProject(filePath: String): Option[String] =
    (!project.isDisposed && Sbt.isProjectDefinitionFile(project, filePath.toFile)).option(project.getBasePath)

  protected def getProjectSettings(file: VirtualFile): Option[SbtProjectSettings] =
    for {
      externalProjectPath <- Option(file.getCanonicalPath).flatMap(getExternalProject)
      sbtSettings <- Option(SbtSystemSettings.getInstance(project))
      projectSettings <- Option(sbtSettings.getLinkedProjectSettings(externalProjectPath))
    } yield {
      projectSettings
    }

  protected def ignoreFile(file: VirtualFile): Unit = ignoredFiles.synchronized {
    ignoredFiles += file
  }

  private def isIgnored(file: VirtualFile): Boolean = ignoredFiles.synchronized {
    ignoredFiles.contains(file)
  }

  private def isSbtFile(file: VirtualFile): Boolean =
    Option(file.getCanonicalPath).flatMap(getExternalProject).isDefined
}
