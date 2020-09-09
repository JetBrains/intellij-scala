package org.jetbrains.plugins.scala.externalHighlighters

import java.util.UUID

import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.project.Project

class CompilerLockBuildManagerListener
  extends BuildManagerListener {

  override def buildStarted(project: Project, sessionId: UUID, isAutomake: Boolean): Unit = {
    HighlightingCompiler.get(project).cancel()
    CompilerLock.get(project).lock(sessionId.toString)
  }

  override def buildFinished(project: Project, sessionId: UUID, isAutomake: Boolean): Unit =
    CompilerLock.get(project).unlock(sessionId.toString, exceptionIfNotLocked = false)
}
