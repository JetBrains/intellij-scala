package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.openapi.project.{Project, ProjectManagerListener}
import org.jetbrains.plugins.scala.lang.formatting.settings.migration.ProjectCodeStyleSettingsMigrationService

private final class ScalaCodeStyleProjectListener extends ProjectManagerListener {

  override def projectOpened(project: Project): Unit = {
    project.getService(classOf[ProjectCodeStyleSettingsMigrationService]).init()
  }
}
