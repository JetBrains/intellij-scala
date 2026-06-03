package org.jetbrains.sbt
package project

import com.intellij.ide.impl.ProjectUtilKt
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor

import javax.swing.Icon
import kotlin.coroutines.Continuation

class SbtProjectOpenProcessor extends ProjectOpenProcessor {

  //noinspection ReferencePassedToNls
  override def getName: String = Sbt.Name
  override def getIcon: Icon = Sbt.Icon

  override def canOpenProject(file: VirtualFile): Boolean =
    SbtProjectImportProvider.canImport(file)

  override def openProjectAsync(virtualFile: VirtualFile,
                                projectOpenOptions: ProjectOpenProcessor.ProjectOpenOptions,
                                continuation: Continuation[? >: Project]): AnyRef =
    new SbtOpenProjectProvider().openProject(
      virtualFile,
      ProjectUtilKt.toOpenProjectTask(projectOpenOptions),
      continuation
    )
}
