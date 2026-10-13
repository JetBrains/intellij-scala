package org.jetbrains.plugins.scala.compiler.highlighting.services
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.{CompilationRequest, HighlightingRequest}


trait HighlightingService extends Disposable:
  
  /** Can be used to notify that we need to trigger highlighting */
  def request(reason: String): Unit

  /** Records that `request`'s file wants highlighting */
  def request(request: HighlightingRequest): Unit

  /**
   * Executes `request` right now, on the calling thread, bypassing the scheduler: no waiting, no
   * planning, no deciding. For instance because it must run within an already-running compilation
   * session. Failures propagate to the caller.
   */
  def compile(request: CompilationRequest): Unit

object HighlightingService:
    def apply(project: Project): HighlightingService =
      project.getService(classOf[HighlightingService])