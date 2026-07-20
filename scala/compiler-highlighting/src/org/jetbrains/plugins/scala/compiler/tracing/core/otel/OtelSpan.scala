package org.jetbrains.plugins.scala.compiler.tracing.core.otel

import io.opentelemetry.api.trace.{Span, StatusCode}
import org.jetbrains.plugins.scala.compiler.tracing.core.TraceSpan
import org.jetbrains.plugins.scala.compiler.tracing.core.events.TraceEvent

/**
 * The concrete handle for an OpenTelemetry span.
 * Encapsulates its own lifecycle, delegating `end` and event additions directly to the underlying OTel Span.
 */
case class OtelSpan[T <: TraceEvent](id: Long, event: T, otelSpan: Span) extends TraceSpan[T] {

  /** Closes the asynchronous span. */
  override private[tracing] def end(): Unit = {
    if (event.args.nonEmpty) {
      event.args.foreach { case (k, v) => otelSpan.setAttribute(k, v) }
    }
    otelSpan.end()
  }

  override def toString: String = s"(id:$id -> ${event.toString})"

  /**
   * Records an error for the asynchronous span and closes it.
   *
   * @param error The event describing the error.
   */
  override def endWithError(error: T): Unit = {
    otelSpan.addEvent(error.name, OtelHelper.buildAttributes(error))
    otelSpan.setStatus(StatusCode.ERROR, error.name)
    otelSpan.end()
  }

  /**
   * Records an event against the open span without closing it.
   *
   * @param evt The event to attach to this span's timeline.
   */
  override def traceEvent(evt: T): Unit = {
    otelSpan.addEvent(evt.name, OtelHelper.buildAttributes(evt))
  }

  /** Returns a copy of this span with a new event. */
  override def withEvent(newEvent: T): TraceSpan[T] = {
    this.copy(event = newEvent)
  }
}
