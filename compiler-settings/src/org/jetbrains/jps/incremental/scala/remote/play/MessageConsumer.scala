package org.jetbrains.jps.incremental.scala.remote.play

/**
 * User: Dmitry.Naydanov
 * Date: 12.02.15.
 */
trait MessageConsumer {
  def consume(message: String)
}
