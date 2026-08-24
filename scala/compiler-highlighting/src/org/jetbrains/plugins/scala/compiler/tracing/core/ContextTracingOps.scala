package org.jetbrains.plugins.scala.compiler.tracing.core

import org.jetbrains.plugins.scala.compiler.tracing.core.events.ContextTraceEvent

/**
 * A tracing operations implementation that automatically manages parent-child context propagation.
 *
 * This class inspects the [[EventContext]] data mixed into the traces to link spans together
 * across different asynchronous phases using an internal `contextRegistry`.
 *
 * === Context Handling & Keys ===
 *  - '''`event.key`''' (Optional): If defined, the span created during `begin()` or `instant()` is
 *    stored in the registry under this key. This allows future asynchronous events to locate it
 *    and attach themselves as children.
 *  - '''`event.parentKey`''' (Optional): If defined, the system looks up this key in the registry
 *    to find the parent span and properly nest the newly created child span.
 *
 * === Memory Management & Avoiding Leaks ===
 * A span registered with a `key` stays in the registry so that later, delayed descendants can resolve it
 * as their parent. Every such entry must eventually be removed or it leaks. An entry leaves the registry
 * in exactly one of two ways, each driven by a flag on the [[EventContext]]:
 *
 *  1. '''Consumed by a child at begin (`closeParent = true`):''' when a child is opened via `begin()` /
 *     `instant()` with `closeParent = true`, [[resolveParent]] destructively reads its parent out of the
 *     registry — the child nests under the parent and frees the parent's entry in one step. A child with
 *     `closeParent = false` only peeks, leaving the parent available for further children.
 *
 *  2. '''Self-removed at end (`closeOnEnd = true`):''' when a span whose event has `closeOnEnd = true` is
 *     passed to [[end]], it removes its '''own''' `key` from the registry. `EventContext.closed()` yields
 *     such an event, so `end(span.closed())` (or `mapAndEnd(key)(_.closed())`) both closes the span and
 *     frees its entry.
 *
 * A span that is neither consumed by a `closeParent = true` child nor ended with `closeOnEnd = true` stays
 * registered forever causing leaks.
 */
class ContextTracingOps[S <: TraceSpan[ContextTraceEvent]](
  service: TracerService[ContextTraceEvent, S],
  lifecycleRegistry: Registry[Any, TraceSpan[ContextTraceEvent]]
) extends TracingOps[ContextTraceEvent] {

  protected val contextRegistry: Registry[Any, S] = Registry()

  /** Resolves the parent span from the registry, respecting the `closeParent` lifecycle flag. */
  private def resolveParent(event: ContextTraceEvent): Option[S] = {
    event.parentKey.flatMap { pKey =>
      if (event.closeParent) {
        contextRegistry.get(pKey)
      } else {
        contextRegistry.peek(pKey)
      }
    }
  }

  /**
   * Ends the given span, and frees its context entry when the span asks for it.
   *
   * When the span's event has `closeOnEnd = true` (typically produced by `EventContext.closed()`), its own
   * `key` is destructively removed from the context registry so the entry does not leak. Otherwise the
   * entry is left in place — either because a `closeParent = true` child will consume it, or because later
   * spans still need to resolve it as their parent. Ending a span therefore never touches any entry other
   * than its own: a sibling ending can't evict the shared parent the others still peek.
   *
   * @param span The span to end.
   */
  override def end(span: TraceSpan[ContextTraceEvent]): Unit = {
    val event = span.event
    if (event.closeOnEnd) event.key.foreach(contextRegistry.get)
    super.end(span)
  }

  /**
   * Starts a new span, automatically resolving its parent and registering its context.
   *
   *  1. Resolves the parent span using `event.parentKey`. If `event.closeParent` is true,
   *     the parent is permanently removed from the registry to free memory.
   *     
   *  2. If `event.key` is defined, the newly created span is saved into the registry so
   *     future asynchronous events can resolve it as their parent.
   *
   * '''Note:''' If you register a span with a key, you are responsible for eventually freeing it — either
   * with a child opened with `closeParent = true` (which consumes it), or by ending it with
   * `closeOnEnd = true`. An `EndEvent` (`closeParent = true`) recorded via [[instant]] against that key is a
   * convenient terminal that consumes the entry.
   *
   * @param event The event describing the span to begin.
   * @return The newly created child span.
   */
  override def begin(event: ContextTraceEvent): TraceSpan[ContextTraceEvent] = {
    val childSpan = service.begin(event, resolveParent(event))
    addToContext(event, childSpan)
    childSpan
  }
  
  /**
   * Records a point-in-time event, immediately closing its span while still managing contexts.
   *
   * Just like `begin()`, this method resolves the parent (and cleans it up if `closeParent = true`),
   * and registers the instant span into the registry if an `event.key` is provided.
   *
   * '''Note:''' Instant spans registered with a `key` remain in the registry even though the span itself is
   * closed, allowing future children to attach to them. That entry is freed the same way as any other:
   * by a `closeParent = true` child consuming it, or by an `EndEvent` referencing this key (also
   * `closeParent = true`). An instant's own `closeOnEnd` is not consulted — it is closed directly, not via
   * [[end]].
   *
   * @param event The point-in-time event to record.
   */
  override def instant(event: ContextTraceEvent): Unit = {
    val instantSpan = service.begin(event, resolveParent(event))
    instantSpan.end()
    addToContext(event, instantSpan)
  }

  private def addToContext(event: ContextTraceEvent, childSpan: S): Unit = {
    event.key.foreach(contextRegistry.add(_, childSpan))
  }
  override private[tracing] def registry: Registry[Any, TraceSpan[ContextTraceEvent]] =
    lifecycleRegistry
}