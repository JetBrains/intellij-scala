package org.jetbrains.plugins.scala.compiler.highlighting.triggers

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.RequestId
import org.jetbrains.plugins.scala.compiler.highlighting.services.BackgroundExecutorService.executeOnBackgroundThreadInNotDisposed
import org.jetbrains.plugins.scala.compiler.highlighting.services.DocumentCompilerAvailabilityService
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.compiler.tracing.core.events.EndEvent

private[highlighting] object OnEditorSelectedTrigger {
  def trigger(project: Project, requestId: RequestId): Unit =
    executeOnBackgroundThreadInNotDisposed(project) {
      // Disable the document compiler.
      DocumentCompilerAvailabilityService(project).disableAll()

      Option(FileEditorManager.getInstance(project).getSelectedEditor)
        .flatMap(editor => Option(editor.getFile))
        .fold(Tracing(project).instant(EndEvent(requestId, "No editor selected"))) { file =>
          OnEditorFocusTrigger.trigger(project, file, requestId)
        }
    }
}