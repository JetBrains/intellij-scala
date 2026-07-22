package org.jetbrains.plugins.scala.compiler.highlighting.services.requests

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.highlighting.compilers.DocumentCompiler
import org.jetbrains.plugins.scala.compiler.highlighting.core.{CompilerEventGeneratingClient, FileCompilationScope}
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.{CompilationRequestPhaseEvent, RequestId}
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.compiler.tracing.core.events.EndEvent
import org.jetbrains.plugins.scala.project.ModuleExt

import scala.concurrent.duration.Deadline

/**
 * A [[DocumentRequest]] that runs inside an already-running compilation, reusing its `client` so the open
 * documents are refreshed within the same compilation session (see `JpsIncrementalRequest` and
 * `BspIncrementalRequest`, which trigger this as soon as their incremental compilation succeeds).
 *
 * Unlike a plain [[DocumentRequest]] it does not start a compilation of its own — no project save, no
 * compile-server startup, no compiler lock, no progress indicator — because the ongoing compilation already
 * holds all of those. It therefore has to run on that compilation's thread, before its client is finished,
 * which is why it is always executed through `CompilerHighlightingService.compile` and never scheduled.
 */
final class SharedClientDocumentRequest(
  scope: FileCompilationScope,
  debugReason: String,
  deadline: Deadline,
  requestId: RequestId,
  project: Project,
  client: CompilerEventGeneratingClient
) extends DocumentRequest(scope, debugReason, deadline, requestId, project) {

  /**
   * Never scheduled, so never delayed. Overridden because the inherited `copy` would return a plain
   * [[DocumentRequest]], silently dropping the shared client and starting a compilation of its own.
   */
  override def delayed(newDeadline: Deadline): SharedClientDocumentRequest = this

  override private[services] def execute(): Unit = {
    val tracer = Tracing(project)
    // The ongoing compilation's request-phase span is already open; open our own for this file.
    tracer.begin(client.compilationId,
      CompilationRequestPhaseEvent(kind, scope.virtualFile.getPath, debugReason, id, client.compilationId))
    try {
      DocumentCompiler.get(project).compile(
        scope.module.findRepresentativeModuleForSharedSourceModuleOrSelf,
        scope.sourceScope,
        scope.document,
        scope.virtualFile,
        client
      )
    } finally {
      var pending = false
      tracer.mapAndEnd(client.compilationId) {
        case req: CompilationRequestPhaseEvent =>
          pending = true
          Some(req.closed())
        case _ => Option.empty
      }
      if (pending) {
        tracer.instant(EndEvent(client.compilationId, "document compilation aborted before starting"))
      }
    }
  }
}
