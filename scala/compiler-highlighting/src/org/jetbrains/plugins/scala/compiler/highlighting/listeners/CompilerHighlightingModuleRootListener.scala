package org.jetbrains.plugins.scala.compiler.highlighting.listeners

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ModuleRootEvent, ModuleRootListener}
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.HighlightingTriggerPhaseEvent
import org.jetbrains.plugins.scala.compiler.highlighting.services.SaveService
import org.jetbrains.plugins.scala.compiler.highlighting.triggers.OnEditorSelectedTrigger
import org.jetbrains.plugins.scala.compiler.tracing.Tracing

private final class CompilerHighlightingModuleRootListener(project: Project) extends ModuleRootListener {
  override def rootsChanged(event: ModuleRootEvent): Unit = {
    if (event.isCausedByWorkspaceModelChangesOnly) {
      // The rootsChanged event is fired multiple times after project sync. Checking for
      // `isCausedByWorkspaceModelChangesOnly` makes sure that we do not trigger multiple compilations.
      if (project.isDisposed) return

      // Ensure that the project will be saved before the next compilation.
      SaveService(project).enableProjectSave()
      val requestId = TriggerPhaseEvents.newRequestId()
      Tracing(project).instant(HighlightingTriggerPhaseEvent(requestId, "module roots changed (workspace model)"))
      OnEditorSelectedTrigger.trigger(project, requestId)
    }
  }
}
