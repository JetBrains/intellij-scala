package org.jetbrains.plugins.scala.compiler.tracing.core


trait Tracer[T] extends AutoCloseable {

  /** Trace an event E converting it to the tracer type T before storing/processing */
  def trace[E](event: E)(using c: TraceTypeConversion[E, T]): Unit

}
trait TraceTypeConversion[I, O] extends (I => O)
