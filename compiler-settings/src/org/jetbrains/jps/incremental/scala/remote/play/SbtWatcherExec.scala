package org.jetbrains.jps.incremental.scala.remote.play

/**
 * User: Dmitry.Naydanov
 * Date: 12.02.15.
 */
trait SbtWatcherExec {
  def startSbtExec(args: Array[String], consumer: MessageConsumer): Unit

  def endSbtExec(): Unit

  def isRunning: Boolean
}
