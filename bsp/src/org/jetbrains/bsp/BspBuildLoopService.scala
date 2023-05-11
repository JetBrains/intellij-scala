package org.jetbrains.bsp

import com.intellij.openapi.application.{ApplicationManager, ModalityState}
import com.intellij.openapi.components.Service
import com.intellij.openapi.externalSystem.service.project.autoimport.FileChangeListenerBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.task.ProjectTaskManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.bsp.settings.{BspProjectSettings, BspSettings}
import org.jetbrains.plugins.scala.project.ProjectExt

import java.util.concurrent.{ScheduledFuture, TimeUnit}

/**
  * Builds bsp modules on file save. We should change this to support the bsp file change notifications.
 * TODO IDEA platform already supports a save-triggered build-in-background mode. Investigate if we can replace this service.
  */
@Service(Array(Service.Level.PROJECT))
final class BspBuildLoopService(project: Project) {

  private def bspSettings: Option[BspProjectSettings] =
    Option(
      BspSettings
        .getInstance(project)
        .getLinkedProjectSettings(project.getBasePath)
    )

  private val busConnection: MessageBusConnection = project.getMessageBus.connect(project.unloadAwareDisposable)
  private val fileIndex = ProjectRootManager.getInstance(project).getFileIndex
  private val taskManager = ProjectTaskManager.getInstance(project)

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
      AppExecutorUtil.getAppScheduledExecutorService.schedule[Unit](()=>(), 0, TimeUnit.NANOSECONDS)

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
      if (isSupported(file)) {
        changesSinceCompile = true
        lastChangeTimestamp = System.nanoTime()
        val module = fileIndex.getModuleForFile(file)
        if (module != null)
          modulesToCompile.add(module)
      }
    }

    private def runCompile(): Unit = {
      changesSinceCompile = false

      def clearOnSuccess(res: ProjectTaskManager.Result): Unit =
        if (res.hasErrors || res.isAborted) {
          // modules stay queued for recompile on next try
          // TODO only re-queue failed modules? requires information to be available in ProjectTaskResult
        } else {
          modulesToCompile.clear()
        }

      val runnable: Runnable = { () =>
        taskManager
          .build(modulesToCompile.toArray: _*)
          .onSuccess(clearOnSuccess(_)): Unit
      }
      ApplicationManager.getApplication.invokeLater(runnable, ModalityState.NON_MODAL)
    }

    // TODO should allow all bsp-compiled types, depending on build server compatibility
    private def isSupported(file: VirtualFile) = file.getExtension match {
      case "scala" => true
      case "java" => true
      case _ => false
    }
  }
}

object BspBuildLoopService {
  def getInstance(project: Project): BspBuildLoopService =
    project.getService(classOf[BspBuildLoopService])
}
