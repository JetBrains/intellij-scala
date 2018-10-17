package org.jetbrains.bsp

import java.util

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.externalSystem.service.project.autoimport.FileChangeListenerBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.task.{ProjectTaskManager, ProjectTaskNotification, ProjectTaskResult}
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.bsp.settings.{BspProjectSettings, BspSettings}
import org.jetbrains.plugins.scala.ScalaFileType

import scala.collection.JavaConverters._

/**
  * Builds bsp modules on file save. We should change this to support the bsp file change notifications
  */
final class BspBuildLoop(project: Project) extends ProjectComponent {

  private def bspSettings: Option[BspProjectSettings] =
    Option(
      BspSettings
        .getInstance(project)
        .getLinkedProjectSettings(project.getBasePath)
    )

  private val busConnection: MessageBusConnection = project.getMessageBus.connect(project)
  private val fileIndex = ProjectRootManager.getInstance(project).getFileIndex
  private val taskManager = ProjectTaskManager.getInstance(project)

  busConnection.subscribe(VirtualFileManager.VFS_CHANGES, FileChangeListener)

  private final object FileChangeListener extends FileChangeListenerBase {

    private val modulesToCompile = scala.collection.mutable.HashSet[Module]()
    private var changesSinceCompile = false

    override def isRelevant(path: String): Boolean = true

    override def apply(): Unit = if (changesSinceCompile && modulesToCompile.nonEmpty) {
      for {
        settings <- bspSettings
        if settings.buildOnSave
      } yield {
        changesSinceCompile = false
        taskManager.build(modulesToCompile.toArray,
          new ProjectTaskNotification {
            override def finished(projectTaskResult: ProjectTaskResult): Unit = {
              if (projectTaskResult.isAborted || projectTaskResult.getErrors > 0) {
                // modules stay queued for recompile on next try
                // TODO only re-queue failed modules? requires information to be available in ProjectTaskResult
              } else {
                modulesToCompile.clear()
              }
            }
          })
      }
    }
    override def updateFile(file: VirtualFile, event: VFileEvent): Unit =
      fileChanged(file, event)
    override def deleteFile(file: VirtualFile, event: VFileEvent): Unit =
      fileChanged(file, event)

    private def fileChanged(file: VirtualFile, event: VFileEvent) =
      if (isSupported(file.getFileType)) {
        changesSinceCompile = true
        val module = fileIndex.getModuleForFile(file)
        modulesToCompile.add(module)
      }

    // TODO should allow all bsp-compiled types
    private def isSupported(path: String) = {
      path.endsWith(".java") ||
      path.endsWith(".scala")
    }


    // TODO should allow all bsp-compiled types
    private def isSupported(fileType: FileType) = fileType match {
      case _ : ScalaFileType => true
      case _ : JavaFileType => true
      case _ => false
    }
  }
}
