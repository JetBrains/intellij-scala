package org.jetbrains.plugins.scala.compiler.highlighting.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.{CompilationRequestsScheduler, ConcurrentPriorityQueue, DeduplicationLogic, PriorityQueue}
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.CompilationRequest

/**
 * Main entry point for compiler-based highlighting: it's possible to either schedule a compilation request
 * asynchronously ([[requestCompilation]]) or compile immediately ([[compile]]).
 */
@Service(Array(Service.Level.PROJECT))
private[highlighting] final class CompilerHighlightingService(project: Project) extends Disposable {

  private val queue: PriorityQueue[CompilationRequest] =
    new ConcurrentPriorityQueue[CompilationRequest]()(using CompilationRequest.compilationRequestOrdering)

  private val scheduler: CompilationRequestsScheduler =
    CompilationRequestsScheduler(project, queue, DeduplicationLogic(project, queue, compile))

  private val progressService: ProjectProgressService = ProjectProgressService(project)

  /** Enqueues `request`; the scheduler merges/deduplicates it and executes it once it is ready. */
  def requestCompilation(request: CompilationRequest): Unit = scheduler.schedule(request)

  /**
   * Executes `request` right now, on the calling thread, bypassing the queue: no debouncing, no
   * deduplication, no deadline. For instance because it must run within an already-running compilation session.
   * Failures propagate to the caller.
   */
  def compile(request: CompilationRequest): Unit =
    if (!project.isDisposed) request.execute()

  override def dispose(): Unit = {
    scheduler.dispose()
    progressService.cancel()
  }
}

private[highlighting] object CompilerHighlightingService {

  def get(project: Project): CompilerHighlightingService =
    project.getService(classOf[CompilerHighlightingService])
}
