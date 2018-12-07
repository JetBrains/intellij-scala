package org.jetbrains.bsp

import java.util.concurrent.{ScheduledFuture, TimeUnit}

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.{ApplicationManager, ModalityState}
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.externalSystem.service.project.autoimport.FileChangeListenerBase
import com.intellij.openapi.fileTypes.{FileType, FileTypeManager}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.task.{ProjectTaskManager, ProjectTaskNotification, ProjectTaskResult}
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.bsp.settings.{BspProjectSettings, BspSettings}
import org.jetbrains.plugins.scala.ScalaFileType

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
  private val fileTypes = FileTypeManager.getInstance()

  busConnection.subscribe(VirtualFileManager.VFS_CHANGES, FileChangeListener)

  private final object FileChangeListener extends FileChangeListenerBase {

    /** Nanoseconds to wait between checking stuff to compile */
    private val checkDelay = 30 * 1000000

    private val modulesToCompile = scala.collection.mutable.HashSet[Module]()
    private var changesSinceCompile = false
    private var lastChangeTimestamp: Long = 0


    /** Delays compilation just a little bit so that it's less likely that multiple builds are triggered for one
      * set of changes. */
    private var scheduledCompile: ScheduledFuture[_] =
      AppExecutorUtil.getAppScheduledExecutorService.schedule[Unit](()=>(),0,TimeUnit.NANOSECONDS)

    private def checkCompile(): Unit = {
      val now = System.nanoTime()
      if ((now - lastChangeTimestamp) > checkDelay
      ) {
        scheduledCompile.cancel(false)
        runCompile()
      }
    }


    override def isRelevant(path: String): Boolean = true

    override def apply(): Unit = if (
      changesSinceCompile &&
        modulesToCompile.nonEmpty &&
        bspSettings.exists(_.buildOnSave) &&
        (scheduledCompile.isCancelled || scheduledCompile.isDone)
    ) {
      scheduledCompile = AppExecutorUtil.getAppScheduledExecutorService
        .scheduleWithFixedDelay(() => checkCompile(), checkDelay, checkDelay, TimeUnit.NANOSECONDS)
    }

    override def updateFile(file: VirtualFile, event: VFileEvent): Unit =
      fileChanged(file, event)
    override def deleteFile(file: VirtualFile, event: VFileEvent): Unit =
      fileChanged(file, event)

    private def fileChanged(file: VirtualFile, event: VFileEvent) = {
      val fileType = fileTypes.getFileTypeByExtension(file.getExtension)
      if (isSupported(fileType)) {
        changesSinceCompile = true
        lastChangeTimestamp = System.nanoTime()
        val module = fileIndex.getModuleForFile(file)
        modulesToCompile.add(module)
      }
    }

    private def runCompile(): Unit = {
      changesSinceCompile = false

      val notification = new ProjectTaskNotification {
        override def finished(projectTaskResult: ProjectTaskResult): Unit = {
          if (projectTaskResult.isAborted || projectTaskResult.getErrors > 0) {
            // modules stay queued for recompile on next try
            // TODO only re-queue failed modules? requires information to be available in ProjectTaskResult
          } else {
            modulesToCompile.clear()
          }
        }
      }

      ApplicationManager.getApplication.invokeLater(
        () => taskManager.build(modulesToCompile.toArray, notification),
        ModalityState.NON_MODAL
      )
    }

    // TODO should allow all bsp-compiled types, depending on build server compatibility
    private def isSupported(fileType: FileType) = fileType match {
      case _ : ScalaFileType => true
      case _ : JavaFileType => true
      case _ => false
    }
  }
}
