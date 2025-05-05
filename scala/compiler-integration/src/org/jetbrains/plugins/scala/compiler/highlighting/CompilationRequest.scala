package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode
import org.jetbrains.plugins.scala.util.DocumentVersion

import scala.concurrent.duration.Deadline

private sealed abstract class CompilationRequest(final val originFiles: Map[VirtualFile, Document], val deadline: Deadline) {
  protected val priority: Int

  final val documentVersions: Map[VirtualFile, DocumentVersion] =
    originFiles.map { case (vf, doc) => vf -> DocumentUtil.documentVersion(vf, doc) }

  val debugReason: String

  def delayed(deadline: Deadline): CompilationRequest
}

private object CompilationRequest {
  /**
   * @param isFirstTimeHighlighting whether worksheet editor has just been selected (is true every time when tabs are switched)
   */
  final case class WorksheetRequest(
    file: ScalaFile,
    virtualFile: VirtualFile,
    document: Document,
    isFirstTimeHighlighting: Boolean,
    debugReason: String,
    override val deadline: Deadline
  ) extends CompilationRequest(Map(virtualFile -> document), deadline) {
    override protected val priority: Int = 1

    override def delayed(deadline: Deadline): WorksheetRequest = copy(deadline = deadline)
  }

  final case class IncrementalRequest(
    fileCompilationScopes: Map[VirtualFile, FileCompilationScope],
    debugReason: String,
    override val deadline: Deadline
  ) extends CompilationRequest(
    fileCompilationScopes.map { case (vf, FileCompilationScope(_, _, _, document, _)) => vf -> document },
    deadline
  ) {
    override protected val priority: Int = 1

    override def delayed(deadline: Deadline): IncrementalRequest = copy(deadline = deadline)
  }

  final case class DocumentRequest(
    scope: FileCompilationScope,
    debugReason: String,
    override val deadline: Deadline
  ) extends CompilationRequest(Map(scope.virtualFile -> scope.document), deadline) {
    override protected val priority: Int = 2

    override def delayed(deadline: Deadline): DocumentRequest = copy(deadline = deadline)
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

  def compilationDeadline: Deadline = Deadline.now + ScalaHighlightingMode.compilationDelay
}
