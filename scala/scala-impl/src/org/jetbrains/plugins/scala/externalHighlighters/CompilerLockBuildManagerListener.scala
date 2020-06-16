package org.jetbrains.plugins.scala.externalHighlighters

import java.util.UUID

import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.externalHighlighters.CompilerLock.From

class CompilerLockBuildManagerListener
  extends BuildManagerListener {

  private val lockSource = From.BuildProcess

  override def buildStarted(project: Project, sessionId: UUID, isAutomake: Boolean): Unit = {
    JpsCompiler.get(project).cancel()
    CompilerLock.get(project).lock(lockSource)
  }

  override def buildFinished(project: Project, sessionId: UUID, isAutomake: Boolean): Unit =
    CompilerLock.get(project).unlock(lockSource)
}
