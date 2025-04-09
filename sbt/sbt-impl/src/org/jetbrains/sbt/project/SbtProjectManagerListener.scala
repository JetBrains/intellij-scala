package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.extensions.{ObjectExt, invokeLater}
import org.jetbrains.sbt.project.settings.ShouldUpdateRunConfigurations
import org.jetbrains.plugins.scala.startup.ProjectActivity
import org.jetbrains.sbt.project.settings.SbtProjectSettings

private final class SbtProjectManagerListener extends ProjectActivity {
  override def execute(project: Project): Unit = invokeLater {
    val linkedProjectSettings = SbtProjectSettings.allForProject(project)
    linkedProjectSettings.foreach { settings =>
      if (!settings.separateProdAndTestSourcesIsExplicit) {
        val isInUse = ScalaCompilerConfiguration(project).separateProdTestSources
        // Displaying a notification here is required for projects where the separate modules for main/test was implicitly enabled in the past.
        // This will also cause the notification to be displayed for projects where the user manually configured the setting
        // in UI. However, since the notification is only displayed once, it’s not a significant issue.
        if (isInUse) {
          SeparateMainTestModulesNotificationListener.showNotificationIfNecessary(settings, project)
        }
        settings.separateProdAndTestSources = isInUse || SbtProjectSettings.DefaultSeparateProdAndTestSources
      }
    }

    SbtProjectSettings.forProject(project).foreach { settings =>
      if (settings.converterVersion < SbtProjectSettings.ConverterVersion) {
        // TODO Only do this if auto-import is enabled? (more predictable, on the other hand, it's not about "build scripts", as the setting claims)
        ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, SbtProjectSystem.Id).use(ProgressExecutionMode.IN_BACKGROUND_ASYNC))
        // The converterVersion will be updated by SbtProjectDataService on a successful refresh.
        settings.converterVersion = SbtProjectSettings.ConverterVersion // TODO Remove (don't trigger another refresh in any case)
      }
    }

    val shouldUpdateRunConfigurations = ShouldUpdateRunConfigurations.getInstance(project)
    if (shouldUpdateRunConfigurations.shouldUpdate) {
      val isDowngrading = shouldUpdateRunConfigurations.isDowngrading.toOption.map(Boolean.unbox)
      UpdateConfigurationImportListener.update(isDowngrading, project)
    }
  }
}
