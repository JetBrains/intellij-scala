package org.jetbrains.sbt.shell

trait CommunicationListener {
  def onCommandQueued(command: String): Unit
  def onCommandPolled(command: String): Unit
  def onCommandFinished(command: String): Unit
}
