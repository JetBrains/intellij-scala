package org.jetbrains.plugins.scala.compiler.highlighting.services.requests

import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager}
import org.jetbrains.plugins.scala.compiler.highlighting.compilation.CompilationDecision
import org.jetbrains.plugins.scala.compiler.highlighting.compilation.CompilationDecision.Incremental
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.RequestId
import org.jetbrains.plugins.scala.compiler.highlighting.services.SaveService
import org.jetbrains.plugins.scala.compiler.highlighting.triggers.TriggerUtil.{fileCompilationScope, moduleFor}
import org.jetbrains.plugins.scala.extensions.{inReadAction, invokeLater}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode
import org.jetbrains.plugins.scala.util.DocumentVersion

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Deadline, FiniteDuration}

/**
 * Contract for a compilation request handled by the scheduler.
 */
trait CompilationRequest {
  /** The files this request compiles, each mapped to its open document. */
  def originFiles: Map[VirtualFile, Document]
  /** When this request becomes eligible for execution. */
  def deadline: Deadline
  /** Identifies the request across its lifecycle (queueing, merging, tracing). */
  def id: RequestId
  /** Human-readable reason the request was triggered, used for logging and tracing. */
  def debugReason: String
  /** Queue ordering weight; higher runs before lower (e.g. incremental before document). */
  def priority: Int
  /** Snapshot of each file's document version, used to detect edits made after enqueueing. */
  def documentVersions: Map[VirtualFile, DocumentVersion]
  /** Creates a copy of this request with a new deadline (used for delayed execution). */
  def delayed(deadline: Deadline): CompilationRequest
  /** Determines whether this request is ready to execute, needs delay, or has expired. */
  def isReadyForExecution: RequestState
  /**
   * Executes/triggers the compilation on the calling thread.
   */
  private[services] def execute(): Unit
  /** The kind of compilation this request performs (incremental, document, worksheet, …). */
  def kind: CompilationKind
}
private val logger = Logger.getInstance(classOf[CompilationRequest])

object CompilationRequest {

  // TODO: This function has side effects, try to avoid it
  def fromDecision(project: Project, virtualFile: VirtualFile, psiFile: PsiFile,
                   document: Document, debugReason: String,
                   requestId: RequestId)(decision: CompilationDecision):Option[CompilationRequest] = {
    val module = moduleFor(project, virtualFile)
    (decision, psiFile) match
      case (_, psi: ScalaFile) if psi.isWorksheetFile => Some(new WorksheetRequest(
        psi, virtualFile, document, !decision.trustsBuildOutput, debugReason, compilationDeadline, requestId, project))
      // Both are answered from the recorded diagnostics by the trigger, which is the only caller. A
      // worksheet reaches the branch above instead, since evaluating it is the only way to highlight it.
      case (CompilationDecision.UpToDate | CompilationDecision.Recorded, _) => None
      case _ if  module eq null => return None
      case (CompilationDecision.Document, _) =>
        val scope = fileCompilationScope(project, virtualFile, module, document, psiFile)
        Some(DocumentRequest(scope, debugReason, compilationDeadline,
          requestId, project))
      case (Incremental(_), _) if CompilerWorkspaceConfiguration.getInstance(project).MAKE_PROJECT_ON_SAVE =>
        invokeLater {
          SaveService(project).saveDocuments(requestId)
          BuildManager.getInstance().scheduleAutoMake()
        }
        None
      case (Incremental(scope), _)  =>
        val scopesMap = inReadAction {
          scope.iterator.flatMap { vf =>
            val fileModule = moduleFor(project, vf)
            if (fileModule == null) None
            else {
              // You'll need to fetch the document and PsiFile for 'vf',
              // not just reuse the ones passed for 'virtualFile'
              val fileDoc = FileDocumentManager.getInstance().getDocument(vf)
              val filePsi = PsiManager.getInstance(project).findFile(vf)

              if (fileDoc != null && filePsi != null)
                Some(vf -> fileCompilationScope(project, vf, fileModule, fileDoc, filePsi))
              else None
            }
          }.toMap
        }
        Some(IncrementalRequest(scopesMap,
          debugReason,
          compilationDeadline, requestId, project
        ))
  }


  /**
   * Used for determining the order of compilation requests in a priority queue. Compilation requests with higher
   * importance should be processed before compilation requests with lower importance. For example, incremental
   * compilation requests have higher priority compared to document compilation requests, since document compilation
   * depends on successful incremental compilation.
   *
   * There is a second part to this process. After a compilation request has been processed, requests that would
   * be subsumed by this request are removed from the priority queue. For example, when an incremental compilation
   * request is processed, there is no need to also run a document compilation request for the same file, since that
   * file will already be compiled by the incremental compilation request.
   *
   * @note Two compilation requests are first compared by their priority field. If the priorities are the same, they are
   *       then ordered by their deadlines.
   */
  implicit val compilationRequestOrdering: Ordering[CompilationRequest] = { (x, y) =>
    val byPriority = x.priority compare y.priority
    if (byPriority != 0) byPriority
    else x.deadline compare y.deadline
  }



  /**
   * How long should we wait before executing a newly created request
   */
  private val NEW_REQUEST_DEADLINE = FiniteDuration(300, TimeUnit.MILLISECONDS)

  /**
   * How long should we wait to retry after a request is `NOT_READY`
   */
  private val RETRY_DEADLINE = NEW_REQUEST_DEADLINE

  def compilationDeadline: Deadline = Deadline.now + NEW_REQUEST_DEADLINE

  def retryDeadline(project: Project): Deadline =
    Deadline.now +
    ScalaHighlightingMode.compilationDelay(project) +
    NEW_REQUEST_DEADLINE
}