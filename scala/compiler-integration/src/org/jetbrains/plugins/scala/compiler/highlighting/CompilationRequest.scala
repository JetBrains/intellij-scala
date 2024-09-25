package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.incremental.scala.remote.SourceScope
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode
import org.jetbrains.plugins.scala.util.DocumentVersion

import scala.concurrent.duration._

private sealed abstract class CompilationRequest(final val originFiles: Map[VirtualFile, Document], val timestamp: Long) {
  protected val priority: Int

  final val documentVersions: Map[VirtualFile, DocumentVersion] = originFiles.map { case (vf, doc) =>
    vf -> DocumentUtil.documentVersion(vf, doc)
  }

  val debugReason: String

  private val deadline: Deadline = Deadline(timestamp.nanoseconds + ScalaHighlightingMode.compilationDelay)

  def remaining: FiniteDuration = deadline.timeLeft

  def delayed(timestamp: Long): CompilationRequest
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
    override val timestamp: Long
  ) extends CompilationRequest(Map(virtualFile -> document), timestamp) {
    override protected val priority: Int = 1

    override def delayed(timestamp: Long): CompilationRequest = copy(timestamp = timestamp)
  }

  final case class IncrementalRequest(
    fileCompilationScopes: Map[VirtualFile, FileCompilationScope],
    debugReason: String,
    override val timestamp: Long
  ) extends CompilationRequest(
    fileCompilationScopes.map { case (vf, FileCompilationScope(_, _, _, document, _)) => vf -> document },
    timestamp
  ) {
    override protected val priority: Int = 1

    override def delayed(timestamp: Long): CompilationRequest = copy(timestamp = timestamp)
  }

  final case class DocumentRequest(
    module: Module,
    sourceScope: SourceScope,
    virtualFile: VirtualFile,
    document: Document,
    debugReason: String,
    override val timestamp: Long
  ) extends CompilationRequest(Map(virtualFile -> document), timestamp) {
    override protected val priority: Int = 2

    override def delayed(timestamp: Long): CompilationRequest = copy(timestamp = timestamp)
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
    if (x.priority != y.priority)
      implicitly[Ordering[Int]].compare(x.priority, y.priority)
    else
      implicitly[Ordering[Deadline]].compare(x.deadline, y.deadline)
  }
}
