package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.Planner.PlannedWork
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.runner.RequestInfo
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.{HighlightingRequest, RequestState}

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.*
/**
 * Keep track of the files that need highlightings.
 * 
 * We keep one entry per file, so a newer request replaces an older one instead of queueing behind it and no
 * request can be held twice. Nothing is decided when a trigger arrives, only when `next()` is called: by
 * then a compilation may have finished and made the answer "repaint what we already have" rather than
 * "compile it again".
 *
 */
private[highlighting] class RequestProvider(protected val runtime: HighlightingRuntime,
                                                  planner: Planner = Planner(),
                                                  retryAfter: FiniteDuration = 250.milliseconds) 
  extends Provider[HighlightingRequest, RequestInfo]:

  protected val held = new AtomicReference(Map.empty[VirtualFile, HighlightingRequest])
  
  /**
   * Records that `request`'s file wants an answer, overwriting older requests for the same file.
   */
  def add(request: HighlightingRequest): Unit =
    held.updateAndGet(_.updated(request.file, request))
    ()

  // Restoring a failed request causes an infinite loop because no successful compilation drains the queue if 
  // the compiler server is failing.
  // To prevent this, short-circuit and discard requests on compiler failure.
  def restore(request: RequestInfo): Unit = ()

  /** Empties the provider  */
  override def dispose(): Unit =
    held.getAndSet(Map.empty).values.toSeq

  /**
   * Decides what to do about everything that is due, and hands over the first piece of work that can run.
   *
   * Files answered from the store are repainted here and dropped, because their work is finished as soon
   * as that is done. Work that cannot run right now is left in place.
   *
   * This function has side effect.
   */
  def next(): Option[RequestInfo] =
    // we also remove invalid files' requests here
    val requests = held.updateAndGet(_.filter((file, _) => file.isValid))
    if requests.isEmpty then None
    else
      val state = runtime.state
      val inputs = runtime.inputsFor(requests.values)
      val plan = planner.plan(state, inputs)
      serve(plan.serveRecorded) // some file might be served
      val request = chooseRequest(plan.work)
      request.map(removeExecutedRequests)

  private def serve(recorded: Iterable[HighlightingRequest]): Unit =
    if recorded.nonEmpty then
      runtime.serveRecorded(recorded.toSet)
      val _ = take(recorded, "Request served from the store")

  /**
   * The first piece of work that can run and can be built.
   *
   * Work that builds to nothing has its files dropped all the same: they cannot be compiled, so asking
   * again would mean asking for ever.
   */
  private def chooseRequest(work: Iterable[PlannedWork]): Option[RequestInfo] =
    val candidates = work.flatMap: item =>
      runtime.readiness(item) match
        case RequestState.Expired =>
          // Expired items can be safely purged immediately
          val _ = take(item.covers.map(_.request), "Request expired")
          Option.empty
        case RequestState.Ready =>
          // See if it can be built, but DO NOT take it from the bucket yet
          runtime.build(item).map(req => (item, req))
        case RequestState.NotReady =>
          Option.empty

    val bestCandidate = candidates.maxByOption:
      case (item, _) => item.covers.map(_.request.sequence).max

    bestCandidate.map:
      case (item, req) =>req -> item.covers.map(_.request)


  /**
   * Removes the entries named by `covers` and returns them.
   *
   * An entry is only removed while it still holds the identity the plan was built from, so a trigger that
   * arrived after the plan was made survives and is answered on the next pull.
   */
  protected def take(covers: Iterable[HighlightingRequest], reason: String = ""): Seq[HighlightingRequest] =
    val previous = held.getAndUpdate: current =>
      current -- covers.collect { case req if current.get(req.file).exists(_.id == req.id) => req.file }
    covers.flatMap(req => previous.get(req.file).filter(_.id == req.id)).toSeq

  /**
   *  We must remove the highlighting requests contained in this `RequestInfo`, since they will be executed by the
   *  corresponding CompilationRequest. However, we retain a request for a given file if we received a more recent request
   *  for that file, after the RequestInfo was created. This means that a new request for a file arrived after
   *  we took the state snapshot. This is a very short time interval; we expect this to happen very rarely.
   */
  protected def removeExecutedRequests(requestInfo: RequestInfo): RequestInfo =
    val (compilationRequest, highlightingRequests) = requestInfo
    val answered = take(highlightingRequests, "Highlighting request executed")
    compilationRequest -> answered