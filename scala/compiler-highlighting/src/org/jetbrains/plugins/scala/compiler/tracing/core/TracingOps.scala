package org.jetbrains.plugins.scala.compiler.tracing.core

/**
 * The tracing API . Two ways to pair a `begin` with its `end`:
 *
 * 1.  '''handle-based''': `val span = begin(event); …; span.end()` (or wrapped via `trace`);
 *
 * 2.  '''keyed''': `begin(key, event); …; end(key)` — the span is stashed in the [[Registry]] under
 * `key` and retrieved at the end-site, so it need not be threaded through the intervening calls.
 *
 * @tparam T The type of the trace event payload.
 */
trait TracingOps[T] {

  /**
   * Opens a new trace span for the specified event and returns its handle.
   *
   * @param event The event payload associated with the start of the span.
   * @return A handle to the newly opened trace span.
   */
  def begin(event: T): TraceSpan[T]

  /**
   * Records a standalone, instantaneous event that is not attached to any ongoing span.
   *
   * @param event The standalone event payload to record.
   */
  def instant(event: T): Unit

  /**
   * The store backing the keyed spans.
   */
  private[tracing] def registry: Registry[Any, TraceSpan[T]]

  /**
   * Closes a previously opened trace span.
   *
   * @param span The handle of the trace span to close.
   */
  def end(span: TraceSpan[T]): Unit = span.end()

  /**
   * Records an instantaneous event within the context of an open trace span.
   *
   * @param span  The handle of the open trace span.
   * @param event The event payload to record within the span.
   */
  def mark(span: TraceSpan[T], event: T): Unit = span.traceEvent(event)

  /**
   * Opens a new trace span for the specified event and stores it in the registry under the given key.
   *
   * @param key   The identifier used to stash and retrieve the span.
   * @param event The event payload associated with the start of the span.
   */
  def begin(key: Any, event: T): Unit = registry.add(key, begin(event))

  /**
   * Retrieves and closes the trace span associated with the given key.
   *
   * @param key The identifier of the stashed trace span to close.
   */
  def end(key: Any): Unit = registry.get(key).foreach(end)

  /**
   * Retrieves the open trace span associated with the given key and records an event within its context.
   *
   * @param key   The identifier of the stashed trace span.
   * @param event The event payload to record within the span.
   */
  def mark(key: Any, event: T): Unit = registry.peek(key).foreach(_.traceEvent(event))

  /**
   * Reassigns an existing trace span from one key to another within the registry.
   *
   * @param fromKey The current identifier of the stashed trace span.
   * @param toKey   The new identifier to associate with the trace span.
   */
  def carry(fromKey: Any, toKey: Any): Unit = registry.carry(fromKey, toKey)

  /**
   * Executes a given block of code within the lifecycle of a newly opened trace span.
   * The span is guaranteed to be closed upon completion or failure of the block.
   *
   * @param event The event payload associated with the span.
   * @param block The computation to execute within the span.
   * @tparam D The return type of the computation.
   * @return The result of the computation.
   */
  def trace[D](event: T)(block: => D): D = {
    val span = begin(event)
    try block finally end(span)
  }

  /**
   * Applies a transformation to the event payload of a stashed trace span.
   * If the transformation yields a new payload, the span is updated in the registry.
   *
   * @param key The identifier of the stashed trace span.
   * @param f   A function that optionally returns a modified event payload.
   */
  def map(key: Any)(f: T => Option[T]): Unit =
    registry.get(key).foreach(s => registry.add(key, map(s)(f)))

  /**
   * Applies a transformation to the event payload of a stashed trace span and subsequently closes the span.
   *
   * @param key The identifier of the stashed trace span.
   * @param f   A function that optionally returns a modified event payload prior to closure.
   */
  def mapAndEnd(key: Any)(f: T => Option[T]): Unit =
    registry.get(key).foreach(span => mapAndEnd(span)(f))

  /**
   * Applies a transformation to the event payload of a provided trace span and subsequently closes it.
   *
   * @param span The handle of the trace span to transform and close.
   * @param f    A function that optionally returns a modified event payload prior to closure.
   */
  def mapAndEnd(span: TraceSpan[T])(f: T => Option[T]): Unit =
    end(map(span)(f))

  /**
   * Completes the lifecycle of an existing stashed span while immediately starting a new span under a new key,
   * applying a transformation to the event payload during the transition.
   *
   * @param fromKey The identifier of the source trace span to close.
   * @param toKey   The identifier for the newly opened trace span.
   * @param f       A function to transform the event payload for the new span.
   */
  def handoff(fromKey: Any, toKey: Any)(f: T => T): Unit =
    registry.get(fromKey).foreach { span => performHandoff(toKey, span)(f) }

  /**
   * Completes the lifecycle of an existing stashed span while immediately starting a new span under a new key.
   * If the source span is missing, starts a new span under the target key using the provided fallback event.
   *
   * @param fromKey  The identifier of the source trace span to close.
   * @param toKey    The identifier for the newly opened trace span.
   * @param fallback The fallback event payload to use if the source span is missing.
   * @param f        A function to transform the event payload for the new span.
   */
  def handoff(fromKey: Any, toKey: Any, fallback: => T)(f: T => T): Unit =
    registry.get(fromKey) match {
      case Some(span) => performHandoff(toKey, span)(f)
      case None => begin(toKey, fallback)
    }

  /**
   * Applies a transformation to the event payload of a given trace span and returns the updated span handle.
   *
   * @param span The handle of the trace span.
   * @param f    A function that optionally returns a modified event payload.
   * @return The updated trace span, or the original span if the transformation yielded `None`.
   */
  def map(span: TraceSpan[T])(f: T => Option[T]): TraceSpan[T] =
    f(span.event).map(span.withEvent).getOrElse(span)

  private def performHandoff(toKey: Any, span: TraceSpan[T])(f: T => T): Unit = {
    begin(toKey, f(span.event))
    end(span)
  }
}

/**
 * Factory operations for instantiating [[TracingOps]].
 */
object TracingOps {

  private class BaseTracingOps[T, S <: TraceSpan[T]](
    s: TracerService[T, S],
    r: Registry[Any, TraceSpan[T]]
  ) extends TracingOps[T] {

    override def begin(event: T): TraceSpan[T] = s.begin(event, None)
    override def instant(event: T): Unit = s.traceEvent(event)
    override def registry: Registry[Any, TraceSpan[T]] = r
  }

  /**
   * Creates a new instance of [[TracingOps]] bridging a [[TracerService]] and a [[Registry]].
   *
   * @param s The underlying tracer service responsible for span generation.
   * @param r The registry used to manage keyed span lifecycle.
   * @tparam T The type of the trace event payload.
   * @tparam S The specific subtype of `TraceSpan` managed by the tracer service.
   * @return A constructed [[TracingOps]] instance.
   */
  def apply[T, S <: TraceSpan[T]](s: TracerService[T, S], r: Registry[Any, TraceSpan[T]]): TracingOps[T] =
    new BaseTracingOps(s, r)
}