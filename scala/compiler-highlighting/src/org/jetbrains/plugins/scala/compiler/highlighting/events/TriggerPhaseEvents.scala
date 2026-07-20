package org.jetbrains.plugins.scala.compiler.highlighting.events

import org.jetbrains.plugins.scala.compiler.tracing.core.events.BaseEvent
import org.jetbrains.plugins.scala.util.CompilationId

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Phase-1 (trigger phase) spans, defined in flow order: everything that happens '''before''' the compile
 * server replies and the highlighting-phase [[HighlightingPhaseEvents.CompilationDurationEvent]] opens
 * (see [[TraceCategory]]).
 *
 * The flow is:
 * [[HighlightingTriggerPhaseEvent]] (a trigger fires) → [[QueueWaitPhaseEvent]] (wait in the priority
 * queue, with [[QueueRescheduledPhaseEvent]] markers) → [[DocumentSavePhaseEvent]] (save unsaved documents,
 * with [[DocumentSaveFailPhaseEvent]] markers) → [[EnsureServerRunningPhaseEvent]] (compile-server startup)
 * → [[CompilerLockWaitPhaseEvent]] (lock acquisition) → [[CompilationRequestPhaseEvent]] (request dispatched,
 * until the first `CompilationStarted`).
 *
 * IDE-driven JPS builds (Build Project / automake) don't go through this dispatch path; they open a
 * [[BuildManagerSessionPhaseEvent]] that wraps their
 * per-module [[HighlightingPhaseEvents.ExternalBuildEvent]]s.
 */
object TriggerPhaseEvents {

  /** Phase-1 span: everything before the compile server replies (see [[TraceCategory.Trigger]]). */
  private[highlighting] class TriggerPhaseEvent(name: String, parentKey: Option[Any], key: Option[Any],
                                                closeParent: Boolean, args: (String, String)*) extends
    BaseEvent(name, parentKey, key, closeParent, false, args.toMap, Some(TraceCategory.Trigger.toString))

  case class RequestId(id: Long, timestamp: Long)

  def newRequestId(): RequestId = RequestId(idGenerator.getAndIncrement(),
    timestamp = System.currentTimeMillis())

  def create(id: RequestId, source: String): HighlightingTriggerPhaseEvent =
    HighlightingTriggerPhaseEvent(id, source)

  private val idGenerator = new AtomicLong(0)

  enum CompilationKind:
    case JPSIncremental, BSPIncremental, Document, InMemoryDocument, Worksheet

    override def toString: String = this match
      case JPSIncremental => "JPS Incremental"
      case BSPIncremental => "BSP Incremental"
      case InMemoryDocument => "Document (In memory)"
      case Document => "Document"
      case Worksheet => "Worksheet"
      case _ => super.toString

  enum LockWaitKind:
    case ProjectReady, JpsBuild

    override def toString: String = this match
      case ProjectReady => "Project Ready"
      case JpsBuild => "JPS Build"

  enum QueueWaitKind:
    case Document, Incremental, Worksheet

  enum QueueWaitOutcome:
    case Superseded, Merged, Dropped, Executed, Disposed, Expired, Cancelled, Error

  /**
   * The first span of the flow: an instant marker emitted whenever a highlighting trigger fires, at each
   * listener that can start a compilation, so the timeline shows a spike per trigger. All triggers share
   * the `name` "trigger"; the kind is in the `source` arg, e.g. "editor focus", "psi change", "module roots
   * changed", "build finished", "highlighting toggled". It is registered under its `RequestId` so the queue
   * wait and the rest of the trigger phase can nest under it (`parent` links it to a build session when the
   * trigger comes from a finished IDE build).
   */
  case class HighlightingTriggerPhaseEvent(id: RequestId, source: String, parent: Any = null)
    extends TriggerPhaseEvent(name = "trigger",
      Option(parent), Some(id), false,
      args = "source" -> source)

  /**
   * The time a compilation request spends waiting in the priority queue, from the moment it is scheduled
   * until it is dispatched to the executor, merged into another request, or dropped.
   *
   * `outcome` is empty while the span is open and is filled in when the span is closed, so the begin event
   * carries only the request description and the end event additionally records how the wait ended (see the
   * call sites in `CompilerHighlightingService` for the possible values).
   */
  case class QueueWaitPhaseEvent(kind: QueueWaitKind, compilationId: RequestId, reason: String, files: String,
                                 outcome: Option[QueueWaitOutcome] = Option.empty)
    extends TriggerPhaseEvent("Queue wait", Some(compilationId), Some(compilationId),
      true,
      args = (Map("kind" -> kind.toString, "reason" -> reason, "files" -> files) ++
        (if (outcome.nonEmpty) Map("outcome" -> outcome.get.toString) else Map())).toSeq*
    ) {
    // A terminal outcome (anything other than Executed) means no downstream phase span will consume this
    // wait from the context registry, so it must drop its own key when it ends. An Executed wait is instead
    // handed off to the compile pipeline (the ensure-server / lock spans consume it as their parent), so it
    // must NOT self-remove.
    override val closeOnEnd: Boolean = outcome.exists(_ != QueueWaitOutcome.Executed)
  }

  /**
   * A zero-duration marker placed on an open [[QueueWaitPhaseEvent]] span each time the request is found not
   * yet ready and is requeued with a later deadline, so the wait shows every reschedule it went through.
   */
  case class QueueRescheduledPhaseEvent(compilationId: RequestId, reason: String) extends
    TriggerPhaseEvent("rescheduled", Some(compilationId), Some(compilationId), true, "reason" -> reason)

  /**
   * The save of unsaved documents performed before an incremental compilation. `requested` is the set of
   * files that had to be saved and `saved` those saved successfully; a file present in `requested` but not
   * `saved` failed (see [[DocumentSaveFailPhaseEvent]]).
   */
  case class DocumentSavePhaseEvent(requestId: RequestId, requested: Set[String] = Set(), saved: Set[String] = Set()) extends
    TriggerPhaseEvent("Document save", Some(requestId), Some(requestId), true,
      "requested" -> requested.mkString(", "),
      "saved" -> saved.mkString(", "))

  /**
   * A marker placed on an open [[DocumentSavePhaseEvent]] span when saving `file` threw, recording the failed
   * file (and an optional `reason`) without aborting the rest of the save.
   */
  case class DocumentSaveFailPhaseEvent(requestId: RequestId, file: String, reason: String = "")
    extends TriggerPhaseEvent("Document save fail", Some(requestId), Some(requestId), true,
      "file" -> file, "reason" -> reason)

  /**
   * Synchronous span around `CompileServerLauncher.ensureServerRunning` — isolates compile-server startup
   * time. No args: server startup is process-wide, not per-file.
   */
  case class EnsureServerRunningPhaseEvent(requestId: RequestId) extends
    TriggerPhaseEvent("Ensure compile server running", Some(requestId), Some(requestId), true)

  /**
   * Synchronous span around acquiring a compiler-lock semaphore in `CompilerLockService.withCompilerLock`.
   * Explains the gap between [[EnsureServerRunningPhaseEvent]] and [[CompilationRequestPhaseEvent]]: the
   * request span only begins once the lock is held. `lock` is "Project Ready" (released after the JPS
   * up-to-date check on startup) or "JPS Build" (the platform-wide IntelliJ JPS build/automake semaphore,
   * held so our compile-server JPS run never overlaps an IntelliJ build on the shared JPS working directory).
   */
  case class CompilerLockWaitPhaseEvent(lock: LockWaitKind, requestId: RequestId) extends
    TriggerPhaseEvent(s"wait: $lock lock", Some(requestId), Some(requestId), true)

  /**
   * The request span: from when a request is dispatched to the compile server until the server replies with
   * the first `CompilationStarted` for the same `compilationId`. It is the last trigger-phase span, and is
   * handed off to the highlighting-phase [[HighlightingPhaseEvents.CompilationDurationEvent]] when
   * `CompilationStarted` arrives.
   *
   * @param kind        the kind of compilation ("Worksheet", "Document", "Document (In memory)",
   *                    "BSP Incremental", "JPS Incremental")
   * @param file        the file(s) the compilation is invoked for (comma-separated when more than one)
   * @param reason      the debug reason that scheduled the compilation
   * @param closeParent whether opening this span consumes its `requestId` parent; `false` for incremental
   *                    compilations, which keep the request chain open for a follow-up document compilation
   * @param closeOnEnd  whether ending this span (without a handoff) removes it from the context registry
   */
  case class CompilationRequestPhaseEvent(kind: CompilationKind, file: String, reason: String, requestId: RequestId,
                                          compilationId: CompilationId,
                                          closeParentValue: Boolean = true,
                                          closeOnEndValue: Boolean = true) extends
    BaseEvent(name = s"${kind.toString}: request → started", Some(requestId), Some(compilationId), closeParentValue, closeOnEndValue,
      Map("kind" -> kind.toString, "file" -> file, "reason" -> reason), Some(TraceCategory.Trigger.toString))

  /**
   * Span for one IDE JPS build session: `BuildManagerListener.buildStarted` → `buildFinished`, keyed by the
   * build's `sessionId`. It is the outer boundary that contains the inner per-compilation
   * [[HighlightingPhaseEvents.ExternalBuildEvent]] spans arriving via the custom-builder bridge. `isAutomake`
   * distinguishes background automake from an explicit Build/Rebuild. `compilationIds` accumulates the
   * external build spans opened under this session (added via `map` when their `CompilationStarted` arrives),
   * so the session end can close any that were left open (e.g. on a cancelled build).
   */
  case class BuildManagerSessionId(id: UUID)
  case class BuildManagerSessionPhaseEvent(isAutomake: Boolean, id: UUID, compilationIds: List[CompilationId] = Nil)
    extends TriggerPhaseEvent("BuildManager session", None, Some(BuildManagerSessionId(id)), true,
      "isAutomake" -> isAutomake.toString)
}
