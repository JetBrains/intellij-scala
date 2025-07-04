package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.ui.GotItTooltip
import org.jetbrains.sbt.SbtBundle

private final class GeneratedManagedSourcesImportListener(project: Project) extends ProjectDataImportListener {
  //noinspection ApiStatus,UnstableApiUsage
  override def onImportFinished(projectPath: String): Unit = {
    if (project.isDisposed) return
    if (GeneratedManagedSourcesService.instance(project).generatedForPath(projectPath)) {
      val sbtButton = SbtTooltip.findSbtToolWindowButton(project)
      sbtButton.foreach { button =>
        val toolWindowManagerDisposable = SbtTooltip.findToolWindowManagerDisposable(project)

        val gtip = new GotItTooltip(
          "sbt.generated.managed.sources.tooltip",
          SbtBundle.message("sbt.generated.managed.sources.during.project.sync"),
          toolWindowManagerDisposable.orNull
        ).withLink(SbtBundle.message("open.sbt.project.settings"), () => SbtTooltip.openSbtProjectSettings(project, projectPath))

        gtip.show(button, GotItTooltip.LEFT_MIDDLE)
      }
    }
  }
}
