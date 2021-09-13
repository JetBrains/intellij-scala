package org.jetbrains.plugins.scala.externalHighlighters

import java.util.UUID

import com.intellij.compiler.server.{BuildManager, BuildManagerListener}
import com.intellij.openapi.project.Project

/**
 * Issue: SCL-17303
 *
 * Drop changes of [[com.intellij.compiler.server.BuildManager.ProjectData]] to fix the problem.
 */
class DropProjectDataChangesBuildManagerListener
  extends BuildManagerListener {

  override def beforeBuildProcessStarted(project: Project, sessionId: UUID): Unit =
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project) &&
      !CompilerHighlightingService.platformAutomakeEnabled(project)) // clearState schedules automake and may lead to infinite compilation
      BuildManager.getInstance.clearState(project)
}
