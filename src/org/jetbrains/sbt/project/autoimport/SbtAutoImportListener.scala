package org.jetbrains.sbt
package project.autoimport

import java.io.File

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileAdapter, VirtualFileEvent}
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.settings.SbtSystemSettings
import org.jetbrains.plugins.scala.extensions

/**
 * @author Nikolay Obedin
 * @since 3/23/15.
 */
class SbtAutoImportListener(project: Project) extends VirtualFileAdapter {
  override def contentsChanged(event: VirtualFileEvent): Unit =
    reimportIfNeeded(event.getFile)

  override def fileDeleted(event: VirtualFileEvent): Unit =
    reimportIfNeeded(event.getFile)

  private def reimportIfNeeded(file: VirtualFile): Unit = {
    val settings = Option(
      SbtSystemSettings.getInstance(project)
        .getLinkedProjectSettings(project.getBasePath))

    if (settings.fold(false)(_.useOurOwnAutoImport) && isBuildFile(file)) {
      extensions.invokeLater {
        if (!project.isDisposed)
          ExternalSystemUtil.refreshProjects(
            new ImportSpecBuilder(project, SbtProjectSystem.Id)
                    .forceWhenUptodate()
                    .use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
          )
      }
    }
  }

  private def isBuildFile(file: VirtualFile): Boolean =
    Option(file.getCanonicalPath).fold(false)(path => Sbt.isProjectDefinitionFile(project, path.toFile))
}
