package org.jetbrains.plugins.scala.compiler.highlighting.services.requests

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.highlighting.compilers.DocumentCompiler
import org.jetbrains.plugins.scala.compiler.highlighting.core.{CompilerEventGeneratingClient, FileCompilationScope}
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.{CompilationRequestPhaseEvent, RequestId}
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.project.ModuleProjectModelExt

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
  requestId: RequestId,
  project: Project,
  client: CompilerEventGeneratingClient,
  closeParent: Boolean = false
) extends DocumentRequest(scope, requestId, project, closeParent) {

  override private[services] def execute(): Unit = {
    val tracer = Tracing(project)
    // The ongoing compilation's request-phase span is already open; open our own for this file.
    tracer.begin(client.compilationId,
      CompilationRequestPhaseEvent(kind, scope.virtualFile.getPath, debugReason, id, client.compilationId,
        closeParentValue = closeParent))
    try {
      DocumentCompiler.get(project).compile(
        scope.module.findRepresentativeModuleForSharedSourceModuleOrSelf,
        scope.sourceScope,
        scope.document,
        scope.virtualFile,
        client
      )
    } finally {
      tracer.mapAndEnd(client.compilationId) {
        case req: CompilationRequestPhaseEvent =>
          Some(req.closed("document compilation failed"))
        case _ => Option.empty
      }
    }
  }
}
