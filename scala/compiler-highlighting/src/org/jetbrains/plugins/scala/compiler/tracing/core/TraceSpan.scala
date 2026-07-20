package org.jetbrains.plugins.scala.compiler.tracing.core

/**
 * A handle to an in-flight asynchronous trace, returned by [[TracerService.begin]].
 *
 * This trait encapsulates its own lifecycle. Implementations of this trait
 * manage the internal state required to safely close the span or append events to it.
 *
 * @tparam T The type of the event payload associated with this span.
 */
trait TraceSpan[T] {
  /** A process-unique id distinguishing this span from every other concurrently-open span. */
  def id: Long

  /** The event data, reused as the label of the closing event. */
  def event: T

  /** Closes the asynchronous span. This method should not be exposed to
   * users but only used internally by tracing tools. */
  private[tracing] def end(): Unit

  /** Records an error for the asynchronous span and closes it. This method should not be exposed to
   * users but only used internally by tracing tools.
   *
   * @param error The event describing the error.
   */
  private[tracing] def endWithError(error: T): Unit

  /** Records an event against the open span without closing it.
   *
   * @param event The event to attach to this span's timeline.
   */
  def traceEvent(event: T): Unit

  /** Returns a copy of this span with a new event. */
  def withEvent(newEvent: T): TraceSpan[T]
}
