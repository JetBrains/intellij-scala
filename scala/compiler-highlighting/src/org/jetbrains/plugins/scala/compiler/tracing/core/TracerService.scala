package org.jetbrains.plugins.scala.compiler.tracing.core

/**
 * The core factory service for starting traces and recording global events.
 * Because [[TraceSpan]] encapsulates its own termination logic, this service acts purely
 * as a span generator and a sink for global, unattached events.
 *
 * @tparam T The type of the trace event (e.g., `TraceEvent`).
 * @tparam S The concrete type of the trace span handle returned by this service.
 */
trait TracerService[T, S <: TraceSpan[T]] extends AutoCloseable{

  /**
   * Records an event at the current point in time, not associated with any span.
   *
   * @param event The event (name + args) describing the event.
   */
  def traceEvent(event: T): Unit

  /**
   * Opens an asynchronous span and returns a handle identifying it. The span must be closed by calling
   * `end()` or `endWithError()` directly on the returned handle.
   *
   * @param event  The event (name + args) to associate with the span.
   * @param parent An optional parent span to link this new span to.
   * @return A self-closing handle identifying the open span.
   */
  def begin(event: T, parent: Option[S]): S
}