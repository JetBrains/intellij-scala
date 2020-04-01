package org.jetbrains.plugins.scala.compiler

import java.util.UUID

import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.project.Project

class CompilerLockBuildManagerListener
  extends BuildManagerListener {

  override def beforeBuildProcessStarted(project: Project, sessionId: UUID): Unit =
    CompilerLock.get(project).lock()

  override def buildFinished(project: Project, sessionId: UUID, isAutomake: Boolean): Unit =
    CompilerLock.get(project).unlock()
}
