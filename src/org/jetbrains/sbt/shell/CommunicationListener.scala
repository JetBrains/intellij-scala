package org.jetbrains.sbt.shell

/**
  * Created by Roman.Shein on 13.04.2017.
  */
trait CommunicationListener {
  def onCommandQueued(command: String): Unit
  def onCommandPolled(command: String): Unit
  def onCommandFinished(command: String): Unit
}
