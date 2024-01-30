package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.compiler.server.{BuildManager, BuildManagerListener}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.AlreadyDisposedException
import org.jetbrains.plugins.scala.compiler.JpsSessionErrorTrackerService
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode

import java.util.UUID

class CompilerLockBuildManagerListener extends BuildManagerListener {

  import org.jetbrains.plugins.scala.compiler.highlighting.CompilerLockBuildManagerListener._

  override def buildStarted(project: Project, sessionId: UUID, isAutomake: Boolean): Unit = suppressAlreadyDisposed {
    CompilerHighlightingService.get(project).cancel()
    CompilerLock.get(project).lock(sessionId.toString)
  }

  override def buildFinished(project: Project, sessionId: UUID, isAutomake: Boolean): Unit = {
    executeOnBuildThread { () =>
      suppressAlreadyDisposed {
        CompilerLock.get(project).unlock(sessionId.toString, exceptionIfNotLocked = false)
        if (ScalaHighlightingMode.showCompilerErrorsScala3(project) && !JpsSessionErrorTrackerService.instance(project).hasError(sessionId)) {
          CompilerHighlightingService.get(project).triggerDocumentCompilationInAllOpenEditors(None)
        }
      }
    }
  }
}

object CompilerLockBuildManagerListener {

  private def suppressAlreadyDisposed(body: => Unit): Unit =
    try {
      body
    } catch {
      case _: AlreadyDisposedException => // ignore
    }

  private def executeOnBuildThread(runnable: Runnable): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode) {
      runnable.run()
    } else {
      BuildManager.getInstance().runCommand(runnable)
    }
  }
}
