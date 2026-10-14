package org.jetbrains.plugins.scala.compiler.highlighting.services.requests

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.highlighting.compilers.DocumentCompiler
import org.jetbrains.plugins.scala.compiler.highlighting.core.FileCompilationScope
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.{CompilationRequestPhaseEvent, RequestId}
import org.jetbrains.plugins.scala.compiler.highlighting.services.util.CompilationUtils
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.project.ModuleProjectModelExt
import org.jetbrains.plugins.scala.util.{CanonicalPath, DocumentVersion}

import scala.collection.immutable.HashMap
import scala.util.control.NonFatal

case class DocumentRequest(
  scope: FileCompilationScope,
  requestId: RequestId,
  protected val project: Project,
  closeParent: Boolean = true,
) extends BaseCompilationRequest(
  project,
  Map(scope.virtualFile -> scope.document),
  requestId
):

  override def kind: CompilationKind =
    if (DocumentCompiler.useInMemoryFile) CompilationKind.InMemoryDocument else CompilationKind.Document
  
  /** Whether [[execute]] blocks until compilation finishes. Overridden by the fire-and-forget post-build variant. */
  protected def awaitCompletion: Boolean = true

  override private[services] def execute(): Unit =
    val FileCompilationScope(virtualFile, module, sourceScope, document, _) = scope
    CompilationUtils.prepareCompilation(project, id, closeParent, awaitCompletion) {
      CompilationUtils.performCompilation(project, id, reportedDocumentVersions, delayIndicator = true, refreshVfs = false) { client =>
        val tracer = Tracing(project)
        tracer.begin(
          client.compilationId,
          CompilationRequestPhaseEvent(kind, virtualFile.getPath, "", id, client.compilationId)
        )
        var msg = ""
        try {
          DocumentCompiler.get(project).compile(
            module.findRepresentativeModuleForSharedSourceModuleOrSelf,
            sourceScope,
            document,
            virtualFile,
            client
          )
        } catch {
          case NonFatal(t) =>
            msg = s"Compilation Failed ${t.getMessage}"
            throw t
        } finally {
          // If the compilation never started (e.g. it was cancelled, or the server failed), no
          // CompilationFinished will arrive to end the span; anything still open here is stranded, so end it.
          // On the normal path it was already handed off and finished, so this is a no-op.
          tracer.mapAndEnd(client.compilationId)(e => Some(e.closed(msg)))
        }
      }
    }

  /**
   * The text this compilation compiles: the document as it is in memory, which is what the document compiler reads.
   */
  private def reportedDocumentVersions: SerializableMap[CanonicalPath, Long] =
    documentVersions.map { case (_, DocumentVersion(path, version)) =>
      path -> version
    }.to(HashMap.mapFactory[CanonicalPath, Long])