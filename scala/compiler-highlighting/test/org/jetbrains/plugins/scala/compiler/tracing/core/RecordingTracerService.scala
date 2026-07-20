package org.jetbrains.plugins.scala.compiler.tracing.core

import scala.collection.mutable

/**
 * A recorded operation on the [[RecordingTracerService]] / its spans. Tests assert on the ordered
 * sequence of these to check that the tracing facade drives the underlying service correctly (which
 * spans were opened, with which parent, in which order, and how they were closed).
 */
sealed trait TraceOp[T] {
  def event: T
}

object TraceOp {
  /** `service.begin(event, parent)` was called, minting a span with `id`. */
  final case class Began[T](id: Long, event: T, parent: Option[RecordingSpan[T]]) extends TraceOp[T]
  /** `span.end()` was called on the span with `id`. */
  final case class Ended[T](id: Long, event: T) extends TraceOp[T]
  /** `span.endWithError(error)` was called on the span with `id`. */
  final case class ErrorEnded[T](id: Long, event: T) extends TraceOp[T]
  /** `span.traceEvent(event)` (a mark) was recorded on the span with `id`. */
  final case class Marked[T](id: Long, event: T) extends TraceOp[T]
  /** `service.traceEvent(event)` — a standalone instant, not attached to any span. */
  final case class Instant[T](event: T) extends TraceOp[T]
}

/**
 * A [[TraceSpan]] test double that records every lifecycle operation into the shared `log` of the
 * [[RecordingTracerService]] that created it, and remembers the `parent` it was opened with.
 */
final class RecordingSpan[T](
  override val id: Long,
  override val event: T,
  val parent: Option[RecordingSpan[T]],
  private val log: mutable.ArrayBuffer[TraceOp[T]]
) extends TraceSpan[T] {

  @volatile var ended = false

  override def end(): Unit = {
    ended = true
    log += TraceOp.Ended(id, event)
  }

  override def endWithError(error: T): Unit = {
    ended = true
    log += TraceOp.ErrorEnded(id, error)
  }

  override def traceEvent(evt: T): Unit =
    log += TraceOp.Marked(id, evt)

  override def withEvent(newEvent: T): TraceSpan[T] =
    new RecordingSpan(id, newEvent, parent, log)
}

/**
 * A [[TracerService]] test double. Instead of talking to a backend it records what it is asked to do
 * into an in-memory [[log]] and hands back self-recording [[RecordingSpan]]s, so unit tests can assert
 * on the exact sequence of tracing operations without any I/O or OpenTelemetry involved.
 *
 * Ids are handed out sequentially (starting at 1), so a test can tell spans apart and pair begins with
 * ends by id.
 */
final class RecordingTracerService[T] extends TracerService[T, RecordingSpan[T]] {

  private val log: mutable.ArrayBuffer[TraceOp[T]] = mutable.ArrayBuffer.empty
  private var counter: Long = 0L

  /** Every operation recorded so far, in order. */
  def ops: List[TraceOp[T]] = log.toList

  /** Just the `begin`s, in order. */
  def begins: List[TraceOp.Began[T]] =
    ops.collect { case b: TraceOp.Began[_] => b.asInstanceOf[TraceOp.Began[T]] }

  /** Drops all recorded operations, so a service can be reused across scenarios. */
  def clear(): Unit = log.clear()

  override def traceEvent(event: T): Unit =
    log += TraceOp.Instant(event)

  override def begin(event: T, parent: Option[RecordingSpan[T]]): RecordingSpan[T] = {
    counter += 1
    val span = new RecordingSpan[T](counter, event, parent, log)
    log += TraceOp.Began(span.id, event, parent)
    span
  }

  override def close(): Unit = {}
}
