package org.jetbrains.plugins.scala.externalHighlighters

import java.util.UUID
import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.AlreadyDisposedException
import org.jetbrains.plugins.scala.externalHighlighters.CompilerLockBuildManagerListener.suppressAlreadyDisposed

class CompilerLockBuildManagerListener
  extends BuildManagerListener {

  override def buildStarted(project: Project, sessionId: UUID, isAutomake: Boolean): Unit = suppressAlreadyDisposed {
    CompilerHighlightingService.get(project).cancel()
    CompilerLock.get(project).lock(sessionId.toString)
  }

  override def buildFinished(project: Project, sessionId: UUID, isAutomake: Boolean): Unit = suppressAlreadyDisposed {
    CompilerLock.get(project).unlock(sessionId.toString, exceptionIfNotLocked = false)
  }
}

object CompilerLockBuildManagerListener {

  private def suppressAlreadyDisposed(body: => Unit): Unit =
    try {
      body
    } catch {
      case _: AlreadyDisposedException => // ignore
    }
}
