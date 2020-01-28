package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import com.intellij.openapi.project.{Project, ProjectManagerListener}

class ScalafmtProjectListener extends ProjectManagerListener {

  override def projectOpened(project: Project): Unit = {
    ScalaFmtSuggesterComponent.instance(project).init()
    ScalafmtDynamicConfigService.instanceIn(project).init()
  }

  override def projectClosed(project: Project): Unit = {
    super.projectClosed(project)
    ScalafmtDynamicConfigService.instanceIn(project).clearCaches()
  }
}
