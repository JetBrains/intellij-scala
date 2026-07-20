package org.jetbrains.plugins.scala.compiler.tracing.core

class NoOpTracingOps[T] extends TracingOps[T] {

  private case class MockSpan(evt: T) extends TraceSpan[T] {
    override def id: Long = -1
    override def event: T = evt
    override def end(): Unit = {}
    override def endWithError(error: T): Unit = {}
    override def traceEvent(event: T): Unit = {}
    override def withEvent(newEvent: T): TraceSpan[T] = this.copy(evt = newEvent)
  }
  
  private val mockRegistry: Registry[Any, TraceSpan[T]] = new Registry[Any, TraceSpan[T]] {
  override def get(key: Any): Option[TraceSpan[T]] = None
  override def peek(key: Any): Option[TraceSpan[T]] = None
  override def add(key: Any, span: TraceSpan[T]): Unit = {}
  override def carry(from: Any, to: Any): Unit = {}
}
  override def end(span: TraceSpan[T]): Unit = {}
  override def instant(event: T): Unit = {}
  override def mark(span: TraceSpan[T], event: T): Unit = {}
  override def registry: Registry[Any, TraceSpan[T]] = mockRegistry
  override def begin(event: T): TraceSpan[T] = MockSpan(event)
}
