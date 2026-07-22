package org.jetbrains.plugins.scala.compiler.highlighting.listeners

import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.JpsSessionErrorTrackerService
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.{BuildManagerSessionId, BuildManagerSessionPhaseEvent, HighlightingTriggerPhaseEvent}
import org.jetbrains.plugins.scala.compiler.highlighting.services.BackgroundExecutorService.executeOnBackgroundThreadInNotDisposed
import org.jetbrains.plugins.scala.compiler.highlighting.services.CompilerLockService
import org.jetbrains.plugins.scala.compiler.highlighting.triggers.DocumentCompilationTrigger
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.compiler.tracing.core.events.EndEvent
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode

import java.util.UUID

private final class CompilerHighlightingBuildManagerListener extends BuildManagerListener {

  // Span for the whole IDE JPS build session, buildStarted opens it, buildFinished closes it.
  override def buildStarted(project: Project, sessionId: UUID, isAutomake: Boolean): Unit =
    Tracing(project).begin(sessionId, BuildManagerSessionPhaseEvent(isAutomake, sessionId))

  override def buildFinished(project: Project, sessionId: UUID, isAutomake: Boolean): Unit = {
    // Close the session span, and with it any external build spans it left open. A module's span is normally
    // closed by its own CompilationFinished; a cancelled build may never emit that, so we close whatever the
    // session still lists here (an already-closed one is a no-op). The session's own context entry is kept for
    // the trigger below and released by the EndEvent(traceSessionId) at the end.
    val tracer = Tracing(project)
    tracer.mapAndEnd(sessionId) {
      case session: BuildManagerSessionPhaseEvent =>
        session.compilationIds.foreach { compilationId =>
          tracer.mapAndEnd(compilationId)(e => Some(e.closed()))
        }
        Some(session)
      case other => Some(other)
    }
    val traceSessionId = BuildManagerSessionId(sessionId)
    executeOnBackgroundThreadInNotDisposed(project) {
      if (CompilerLockService.instance(project).isReady &&
        ScalaHighlightingMode.showCompilerErrorsScala3(project) &&
        !JpsSessionErrorTrackerService.instance(project).hasError(sessionId)) {
        val requestId = TriggerPhaseEvents.newRequestId()
        tracer.instant(HighlightingTriggerPhaseEvent(requestId, "build finished",
          traceSessionId ))
        val dispatched =
          DocumentCompilationTrigger.triggerDocumentCompilationInAllOpenEditors(project, "On Build Finish", requestId)
        // The trigger's requestId context is consumed by the dispatched document compilation.
        // If nothing was dispatched (e.g. no eligible Scala editor when the rebuild is cancelled), nothing
        // will consume it, so close it explicitly.
        if (!dispatched) {
          tracer.instant(EndEvent(requestId, "no document compilation scheduled after build"))
        }
        // The session is a keyed root whose children only peek it to enable multiple children
        // (one per module), so nothing consumes it from the context registry.
        // Release it explicitly now that the trigger has captured it as its parent.
        tracer.instant(EndEvent(traceSessionId, "build finished"))
      } else {
        tracer.instant(EndEvent(traceSessionId, "Build canceled"))
      }
    }
  }
}
