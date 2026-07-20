package org.jetbrains.plugins.scala.compiler.tracing.core.events

/**
 * Metadata a [[TraceEvent]] carries so the context-tracing ops can link spans across the asynchronous
 * phases of a compilation and know when to drop them from the context registry. Four fields drive this:
 *
 *  - `key`         — the id this span is registered under, so a later (possibly asynchronous) event can
 *                    find it and nest under it. If `None`, the span is not registered and cannot be a parent.
 *  - `parentKey`   — the id of the span this one nests under; looked up in the registry when the span opens.
 *  - `closeParent` — read at '''begin''': `true` consumes the parent out of the registry (a destructive read),
 *                    `false` only peeks it so later siblings can still resolve it.
 *  - `closeOnEnd`  — read at '''end''': `true` removes this span's own `key` from the registry when it ends.
 *
 * A registered span is therefore freed in one of two ways: a `closeParent = true` child consumes it, or it
 * is ended with `closeOnEnd = true` (see [[EventContext.EventContextOps.closed]]). Anything else leaks.
 */
trait EventContext {
  def parentKey: Option[Any]
  def key: Option[Any]
  def closeParent: Boolean
  def closeOnEnd: Boolean
}


object EventContext {
  private case class EventWrapper(evt: ContextTraceEvent,
                                  override val closeParent: Boolean,
                                  override val closeOnEnd: Boolean)
    extends ContextTraceEvent {
    export evt.{key, parentKey, name}
    // `args` and `category` are concrete in TraceEvent, so they are already members here and cannot be
    // exported. They still have to be forwarded: a span re-applies `args` when it closes, so dropping them
    // would discard whatever was recorded on the event just before it was wrapped.
    override def args: Map[String, String] = evt.args
    override def category: Option[String] = evt.category
  }
  extension(evt: ContextTraceEvent)
    /** Marks the event so that ending its span removes the event's own `key` from the context registry.*/
    def closed(): ContextTraceEvent = EventWrapper(evt, evt.closeParent, true)
    /** Marks the event so that opening its span removes the event's `parentKey` from the context registry.*/
    def parentClosed(): ContextTraceEvent = EventWrapper(evt, true, evt.closeOnEnd)
}

