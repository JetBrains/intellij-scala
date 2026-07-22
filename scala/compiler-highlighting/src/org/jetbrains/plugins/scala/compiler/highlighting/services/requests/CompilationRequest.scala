package org.jetbrains.plugins.scala.compiler.highlighting.services.requests

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.RequestId
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode
import org.jetbrains.plugins.scala.util.DocumentVersion

import scala.concurrent.duration.Deadline

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

object CompilationRequest {

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

  def compilationDeadline(project: Project): Deadline = Deadline.now + ScalaHighlightingMode.compilationDelay(project)
}