package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.ui.GotItTooltip
import org.jetbrains.plugins.scala.settings.ShowSettingsUtilImplExt
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.settings.SbtExternalSystemConfigurable

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
        ).withLink(SbtBundle.message("open.sbt.project.settings"), () =>
          ShowSettingsUtilImplExt.showSettingsDialog(project, classOf[SbtExternalSystemConfigurable], SbtBundle.message("generate.managed.sources.during.project.sync.label"))
        )

        gtip.show(button, GotItTooltip.LEFT_MIDDLE)
      }
    }
  }
}
