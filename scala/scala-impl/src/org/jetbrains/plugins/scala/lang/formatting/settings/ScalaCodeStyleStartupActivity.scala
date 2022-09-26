package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.plugins.scala.lang.formatting.settings.inference.CodeStyleSettingsInferService
import org.jetbrains.plugins.scala.lang.formatting.settings.migration.ProjectCodeStyleSettingsMigrationService

private final class ScalaCodeStyleStartupActivity extends StartupActivity {
  override def runActivity(project: Project): Unit = {
    project.getService(classOf[CodeStyleSettingsInferService]).init()
    project.getService(classOf[ProjectCodeStyleSettingsMigrationService]).init()
  }
}
