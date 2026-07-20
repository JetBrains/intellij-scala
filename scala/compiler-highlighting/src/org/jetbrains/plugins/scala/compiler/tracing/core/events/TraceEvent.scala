package org.jetbrains.plugins.scala.compiler.tracing.core.events

/**
 * A user-supplied description of something to trace: a `name`, optional `args`, and an optional
 * `category`. */
trait TraceEvent {
  def name: String

  def args: Map[String, String] 

  def category: Option[String]
}

object TraceEvent {
  def apply(n: String, c: Option[String] = None, a: Map[String, String] = Map.empty): TraceEvent = new TraceEvent {
    override def name: String = n

    override def category: Option[String] = c

    override def args: Map[String, String] = a
  }
}