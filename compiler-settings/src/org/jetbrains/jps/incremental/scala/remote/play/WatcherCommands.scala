package org.jetbrains.jps.incremental.scala.remote.play

/**
 * User: Dmitry.Naydanov
 * Date: 12.02.15.
 */
object WatcherCommands {
  val START = "start"
  val STOP = "stop"
  val IS_RUNNING = "running"
  val LOOP = "loop"
  val TRUE = "true"
  val FALSE = "false"

  def toMessage(v: Boolean) = if (v) TRUE else FALSE
}
