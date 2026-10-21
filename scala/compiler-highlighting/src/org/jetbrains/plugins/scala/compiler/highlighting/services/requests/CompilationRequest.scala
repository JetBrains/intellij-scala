package org.jetbrains.plugins.scala.compiler.highlighting.services.requests

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.compiler.highlighting.core.FileCompilationScope
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.RequestId
import org.jetbrains.plugins.scala.compiler.highlighting.triggers.TriggerUtil.{fileCompilationScope, moduleFor}
import org.jetbrains.plugins.scala.util.DocumentVersion

/**
 * Contract for a compilation request handled by the scheduler.
 */
trait CompilationRequest:
  /** The files this request compiles, each mapped to its open document. */
  def originFiles: Map[VirtualFile, Document]
  /** Identifies the request across its lifecycle (queueing, merging, tracing). */
  def id: RequestId

  /** Snapshot of each file's document version, used to detect edits made after enqueueing. */
  def documentVersions: Map[VirtualFile, DocumentVersion]
  /** Determines whether this request is ready to execute, needs delay, or has expired. */
  def isReadyForExecution: RequestState
  /**
   * Executes/triggers the compilation on the calling thread.
   */
  private[services] def execute(): Unit
  /** The kind of compilation this request performs (incremental, document, worksheet, …). */
  def kind: CompilationKind

object CompilationRequest:
  
  /**
   * A build over `scope`.
   *
   * Every file is resolved on its own rather than reusing anything from the file the decision was asked
   * about, since the scope holds other files by construction. A file with no module, document or PSI file
   * is dropped instead of failing the request, and a scope that resolves to nothing produces no request.
   *
   * The scope can hold every unbuilt file of the project, so it is resolved in a non-blocking read action,
   * checking for cancellation per file: a write action interrupts it and it starts over, rather than waiting.
   */
  def incremental(project: Project, scope: Set[VirtualFile],
                  requestId: RequestId): Option[CompilationRequest] = 
    val scopes = ReadAction.nonBlocking[Map[VirtualFile, FileCompilationScope]](() =>
      scope.iterator.flatMap { file =>
        ProgressManager.checkCanceled()
        val module = moduleFor(project, file)
        if (module == null) None
        else {
          val document = FileDocumentManager.getInstance().getDocument(file)
          val psiFile = PsiManager.getInstance(project).findFile(file)
          if (document != null && psiFile != null)
            Some(file -> fileCompilationScope(project, file, module, document, psiFile))
          else None
        }
      }.toMap
    )
      .expireWhen(() => project.isDisposed)
      .executeSynchronously()
    if (scopes.isEmpty) None
    else Some(IncrementalRequest(scopes, requestId, project))