package org.jetbrains.bsp

import java.util

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.externalSystem.service.project.autoimport.FileChangeListenerBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.psi.PsiManager
import com.intellij.task.{ProjectTaskManager, ProjectTaskNotification, ProjectTaskResult}
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.plugins.scala.ScalaFileType
import scala.collection.JavaConverters._


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

    override def after(fileEvents: util.List[_ <: VFileEvent]): Unit = {

      val eventModules = for {
        event <- Option(fileEvents).map(_.asScala).getOrElse(Seq.empty)
        file = event.getFile
        if isSupported(file.getFileType)
        module <- Option(fileIndex.getModuleForFile(file))
      } yield module

      val changedModules = eventModules.distinct
      val modulesToCompile = (changedModules ++ failedModules).toArray

      for {
        settings <- bspSettings
        if settings.buildOnSave
      } yield {
        taskManager.build(modulesToCompile,
          new ProjectTaskNotification {
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


    // TODO should allow all bsp-compiled types
    private def isSupported(fileType: FileType) = fileType match {
      case _ : ScalaFileType => true
      case _ : JavaFileType => true
      case _ => false
    }
  }
}
