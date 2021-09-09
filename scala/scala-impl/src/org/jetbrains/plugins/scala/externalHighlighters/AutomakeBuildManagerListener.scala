package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.project.Project

import java.util.UUID

class AutomakeBuildManagerListener extends BuildManagerListener {
  override def buildFinished(project: Project, sessionId: UUID, isAutomake: Boolean): Unit = {
    if (isAutomake && ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
      TriggerCompilerHighlightingService.get(project).afterIncrementalCompilation()
    }
  }
}
