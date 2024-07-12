package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.{Project, ProjectManagerListener}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.sbt.project.settings.SbtProjectSettings

private final class SbtProjectManagerListener extends ProjectManagerListener {
  override def projectOpened(project: Project): Unit = {
    SbtProjectSettings.forProject(project).foreach { settings =>
      if (settings.converterVersion < SbtProjectSettings.ConverterVersion) {
        if ((project.hasScala3 && settings.preferedScala == 1) || (project.hasScala2 && settings.preferedScala == 2)) { // TODO Remove (don't trigger the refresh unnecessarily)
          // TODO Only do this if auto-import is enabled? (more predictable, on the other hand, it's not about "build scripts", as the setting claims)
          ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, SbtProjectSystem.Id).use(ProgressExecutionMode.IN_BACKGROUND_ASYNC))
          // The converterVersion will be updated by SbtProjectDataService on a successful refresh.
          settings.converterVersion = SbtProjectSettings.ConverterVersion // TODO Remove (don't trigger another refresh in any case)
        }
      }
    }
  }
}
