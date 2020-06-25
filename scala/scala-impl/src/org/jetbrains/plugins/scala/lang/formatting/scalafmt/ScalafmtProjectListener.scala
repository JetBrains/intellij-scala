package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import com.intellij.openapi.project.{Project, ProjectManagerListener}

class ScalafmtProjectListener extends ProjectManagerListener {

  override def projectOpened(project: Project): Unit = {
    ScalaFmtSuggesterService.instance(project).init()
    ScalafmtDynamicConfigService.instanceIn(project).init()
  }

  override def projectClosing(project: Project): Unit =
    ScalafmtDynamicConfigService.instanceIn(project).clearCaches()
}
