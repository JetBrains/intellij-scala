package org.jetbrains.bsp

import java.util

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.externalSystem.service.project.autoimport.FileChangeListenerBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.psi.PsiManager
import com.intellij.task.{ProjectTaskManager, ProjectTaskNotification, ProjectTaskResult}
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.plugins.scala.ScalaFileType

final class BspBuildLoop(project: Project) extends AbstractProjectComponent(project) {

  private def bspSettings: Option[BspProjectSettings] =
    Option(
      BspSystemSettings
        .getInstance(project)
        .getLinkedProjectSettings(project.getBasePath)
    )

  private val busConnection: MessageBusConnection = myProject.getMessageBus.connect(project)
  private val fileIndex = ProjectRootManager.getInstance(project).getFileIndex
  private val psiManager: PsiManager = PsiManager.getInstance(project)
  private val taskManager = ProjectTaskManager.getInstance(project)
  private val failedModules = scala.collection.mutable.HashSet[Module]()

  busConnection.subscribe(VirtualFileManager.VFS_CHANGES, FileChangeListener)

  private final object FileChangeListener extends FileChangeListenerBase {
    // We cannot tell if it's relevant yet at this stage
    override def isRelevant(path: String): Boolean = true
    override def apply(): Unit = ()
    override def updateFile(file: VirtualFile, event: VFileEvent): Unit = ()
    override def deleteFile(file: VirtualFile, event: VFileEvent): Unit = ()

    override def after(events0: util.List[_ <: VFileEvent]): Unit = {
      if (events0 != null && !bspSettings.exists(_.buildOnSave)) ()
      else {
        import scala.collection.JavaConverters._
        val modules = events0.asScala.toList.flatMap { event =>
          val file = event.getFile
          // TODO should allow all bsp-compiled types
          val isSupportedFileType = Option(psiManager.findFile(file).getFileType).exists(
            t => t.isInstanceOf[ScalaFileType] || t.isInstanceOf[JavaFileType])
          if (!isSupportedFileType) Nil
          else List(fileIndex.getModuleForFile(file))
        }

        val changedModules = modules.distinct
        val modulesToCompile = (changedModules ++ failedModules).toArray
        if (changedModules.nonEmpty) {
          taskManager.build(modulesToCompile, new ProjectTaskNotification {
            override def finished(projectTaskResult: ProjectTaskResult): Unit = {
              if (projectTaskResult.isAborted || projectTaskResult.getErrors > 0) {
                failedModules.++=(modulesToCompile)
              } else {
                failedModules.clear()
              }
            }
          })
        }
      }
    }
  }
}
