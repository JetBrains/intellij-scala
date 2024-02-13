package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.{JpsSessionErrorTrackerService, executeOnBuildThread}
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode

import java.util.UUID

private final class CompilerHighlightingBuildManagerListener extends BuildManagerListener {

  override def buildFinished(project: Project, sessionId: UUID, isAutomake: Boolean): Unit = {
    executeOnBuildThread { () =>
      if (!project.isDisposed) {
        if (ScalaHighlightingMode.showCompilerErrorsScala3(project) && !JpsSessionErrorTrackerService.instance(project).hasError(sessionId)) {
          CompilerHighlightingService.get(project).triggerDocumentCompilationInAllOpenEditors(None)
        }
      }
    }
  }
}
