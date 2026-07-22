package org.jetbrains.plugins.scala.compiler.highlighting.services.requests

import com.intellij.openapi.project.{DumbService, Project}
import org.jetbrains.plugins.scala.compiler.highlighting.compilers.DocumentCompiler
import org.jetbrains.plugins.scala.compiler.highlighting.core.FileCompilationScope
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.{CompilationRequestPhaseEvent, RequestId}
import org.jetbrains.plugins.scala.compiler.highlighting.services.util.CompilationUtils
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.util.{CanonicalPath, DocumentVersion}

import scala.collection.immutable.HashMap
import scala.concurrent.duration.Deadline

case class DocumentRequest(
  scope: FileCompilationScope,
  debugReason: String,
  override val deadline: Deadline,
  requestId: RequestId,
  protected val project: Project
) extends BaseCompilationRequest(
  Map(scope.virtualFile -> scope.document),
  deadline,
  requestId
) {
  override val priority: Int = 2

  override def kind: CompilationKind =
    if (DocumentCompiler.useInMemoryFile) CompilationKind.InMemoryDocument else CompilationKind.Document
    
  override def delayed(newDeadline: Deadline): DocumentRequest = copy(deadline = newDeadline)

  override def isReadyForExecution: RequestState = {
    if (isExpired) return RequestState.Expired

    if (deadline.isOverdue()) {
      if (DumbService.isDumb(project)) return RequestState.NotReady
      canDocumentBeCompiled(project, scope.document)
    } else {
      RequestState.NotReady
    }
  }

  /** Whether [[execute]] blocks until compilation finishes. Overridden by the fire-and-forget post-build variant. */
  protected def awaitCompletion: Boolean = true

  override private[services] def execute(): Unit = {
    val FileCompilationScope(virtualFile, module, sourceScope, document, _) = scope
    CompilationUtils.prepareCompilation(project, id, awaitCompletion) {
      CompilationUtils.performCompilation(project, id, reportedDocumentVersions, delayIndicator = true, refreshVfs = false) { client =>
        val tracer = Tracing(project)
        tracer.begin(
          client.compilationId,
          CompilationRequestPhaseEvent(kind, virtualFile.getPath, debugReason, id, client.compilationId)
        )
        try {
          DocumentCompiler.get(project).compile(
            module.findRepresentativeModuleForSharedSourceModuleOrSelf,
            sourceScope,
            document,
            virtualFile,
            client
          )
        } finally {
          // If the compilation never started (e.g. it was cancelled, or the server failed), no
          // CompilationFinished will arrive to end the span; anything still open here is stranded, so end it.
          // On the normal path it was already handed off and finished, so this is a no-op.
          tracer.mapAndEnd(client.compilationId)(e => Some(e.closed()))
        }
      }
    }
  }

  /**
   * The document versions reported to the compilation, used to discard the results if the document has changed
   * since the request was created. Overridden by the post-build variant to report none (always apply).
   */
  protected def reportedDocumentVersions: SerializableMap[CanonicalPath, Long] =
    documentVersions.map { case (_, DocumentVersion(path, version)) =>
      path -> version
    }.to(HashMap.mapFactory[CanonicalPath, Long])
}