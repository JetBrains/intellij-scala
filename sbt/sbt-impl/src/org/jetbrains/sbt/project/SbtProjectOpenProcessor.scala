package org.jetbrains.sbt
package project

import com.intellij.ide.impl.ProjectUtilKt.runUnderModalProgressIfIsEdt
import com.intellij.ide.impl.{OpenProjectTask, ProjectUtilKt}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor

import javax.swing.Icon
import kotlin.coroutines.Continuation
import scala.annotation.nowarn

class SbtProjectOpenProcessor extends ProjectOpenProcessor {

  //noinspection ReferencePassedToNls
  override def getName: String = Sbt.Name
  override def getIcon: Icon = Sbt.Icon

  override def canOpenProject(file: VirtualFile): Boolean =
    SbtProjectImportProvider.canImport(file)

  @deprecated("Use openProjectAsync(VirtualFile, ProjectOpenOptions) instead")
  override def doOpenProject(virtualFile: VirtualFile, projectToClose: Project, forceOpenInNewFrame: Boolean): Project =
    runUnderModalProgressIfIsEdt { (_, continuation) =>
      openProject(
        virtualFile,
        toOpenProjectTask(projectToClose, forceOpenInNewFrame),
        continuation
      )
    }: @nowarn("cat=deprecation")

  @deprecated("Use openProjectAsync(VirtualFile, ProjectOpenOptions) instead")
  override def openProjectAsync(virtualFile: VirtualFile,
                                projectToClose: Project,
                                forceOpenInNewFrame: Boolean,
                                continuation: Continuation[? >: Project]): AnyRef =
    openProject(
      virtualFile,
      toOpenProjectTask(projectToClose, forceOpenInNewFrame),
      continuation
    )

  override def openProjectAsync(virtualFile: VirtualFile,
                                projectOpenOptions: ProjectOpenProcessor.ProjectOpenOptions,
                                continuation: Continuation[? >: Project]): AnyRef =
    openProject(
      virtualFile,
      ProjectUtilKt.toOpenProjectTask(projectOpenOptions),
      continuation
    )

  private def toOpenProjectTask(projectToClose: Project, forceOpenInNewFrame: Boolean): OpenProjectTask =
    OpenProjectTask.build().withProjectToClose(projectToClose).withForceOpenInNewFrame(forceOpenInNewFrame)

  private def openProject(virtualFile: VirtualFile, openProjectTask: OpenProjectTask, continuation: Continuation[? >: Project]): AnyRef =
    new SbtOpenProjectProvider().openProject(virtualFile, openProjectTask, continuation)
}
