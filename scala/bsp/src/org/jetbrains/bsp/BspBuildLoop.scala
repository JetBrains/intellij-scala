package org.jetbrains.bsp

import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.externalSystem.service.project.autoimport.FileChangeListenerBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.psi.PsiManager
import com.intellij.task.ProjectTaskManager
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.plugins.scala.ScalaFileType

class BspBuildLoop(project: Project) extends AbstractProjectComponent(project) {

  private val bspSettings: BspProjectSettings =
    BspSystemSettings.getInstance(project).getLinkedProjectSettings(project.getBasePath)

  private val busConnection: MessageBusConnection = myProject.getMessageBus.connect(project)
  busConnection.subscribe(VirtualFileManager.VFS_CHANGES, FileChangeListener)

  private object FileChangeListener extends FileChangeListenerBase {
    override def isRelevant(path: String): Boolean = true
    override def apply(): Unit = () // ???

    override def updateFile(file: VirtualFile, event: VFileEvent): Unit =
      buildModule(file)

    override def deleteFile(file: VirtualFile, event: VFileEvent): Unit =
      buildModule(file)

    private val fileIndex = ProjectRootManager.getInstance(project).getFileIndex
    private val psiManager: PsiManager = PsiManager.getInstance(project)
    private val taskManager = ProjectTaskManager.getInstance(project)

    // TODO can maybe collect changes in some cases
    private def buildModule(file: VirtualFile): Unit = {
      if (!bspSettings.buildOnSave) return

      val module = fileIndex.getModuleForFile(file)
      // TODO should allow all bsp-compiled types
      val isSupportedFileType =
        Option(psiManager.findFile(file))
          .exists(_.getFileType.isInstanceOf[ScalaFileType])

      if (isSupportedFileType)
        taskManager.build(module)
    }
  }

}
