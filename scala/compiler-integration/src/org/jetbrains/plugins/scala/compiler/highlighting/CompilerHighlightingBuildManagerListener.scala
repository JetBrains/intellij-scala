package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.JpsSessionErrorTrackerService
import org.jetbrains.plugins.scala.compiler.highlighting.BackgroundExecutorService.executeOnBackgroundThreadInNotDisposed
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode

import java.util.UUID

private final class CompilerHighlightingBuildManagerListener extends BuildManagerListener {

  override def buildFinished(project: Project, sessionId: UUID, isAutomake: Boolean): Unit = {
    executeOnBackgroundThreadInNotDisposed(project) {
      if (CompilerLockService.instance(project).isReady &&
        ScalaHighlightingMode.showCompilerErrorsScala3(project) &&
        !JpsSessionErrorTrackerService.instance(project).hasError(sessionId)) {
        CompilerHighlightingService.get(project).triggerDocumentCompilationInAllOpenEditors(None)
      }
    }
  }
}
