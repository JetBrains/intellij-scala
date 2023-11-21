package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.jps.incremental.scala.remote.SourceScope
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode

import scala.concurrent.duration._

private sealed trait CompilationRequest {
  val priority: Int
  val virtualFile: VirtualFile
  val document: Document
  val debugReason: String

  final val compilationDelay: FiniteDuration = ScalaHighlightingMode.compilationDelay

  private val timestamp: Long = System.nanoTime()

  private val deadline: Deadline = Deadline(timestamp.nanoseconds + compilationDelay)

  def remaining: FiniteDuration = deadline.timeLeft

  def delayed: CompilationRequest
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
  ) extends CompilationRequest {
    override val priority: Int = 1

    override def delayed: WorksheetRequest = this.copy()
  }

  final case class IncrementalRequest(
    module: Module,
    sourceScope: SourceScope,
    virtualFile: VirtualFile,
    document: Document,
    psiFile: PsiFile,
    debugReason: String
  ) extends CompilationRequest {
    override val priority: Int = 1

    override def delayed: IncrementalRequest = this.copy()
  }

  final case class DocumentRequest(
    module: Module,
    sourceScope: SourceScope,
    virtualFile: VirtualFile,
    document: Document,
    debugReason: String
  ) extends CompilationRequest {
    override val priority: Int = 2

    override def delayed: DocumentRequest = this.copy()
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
   *       then ordered by their deadlines. If it happens that the deadlines match, ties are broken by the path of the
   *       file they are scheduled for.
   */
  implicit val compilationRequestOrdering: Ordering[CompilationRequest] = { (x, y) =>
    if (x.priority != y.priority)
      implicitly[Ordering[Int]].compare(x.priority, y.priority)
    else {
      val byDeadline = implicitly[Ordering[Deadline]].compare(x.deadline, y.deadline)
      if (byDeadline != 0)
        byDeadline
      else
        implicitly[Ordering[String]].compare(x.virtualFile.getCanonicalPath, y.virtualFile.getCanonicalPath)
    }
  }
}
