package org.jetbrains.sbt.shell

import com.intellij.util.concurrency.Semaphore

import org.jetbrains.sbt.shell.SbtShellCommunication.{ShellEvent, TaskComplete, TaskStart}

case class SettingQueryHandler(settingName: String, taskName: String, comm: SbtShellCommunication) extends SbtShellCommunication.EventHandler {
  private val buffer = new StringBuilder()
  val semaphore = new Semaphore()

  def getSettingValue(): String = {
    semWrap(comm.command("show " + settingColon , this, showShell = false))
    buffer.mkString
  }

  def addToSettingValue(add: String): Unit = {
    semWrap(comm.command("set " + settingColon + "+=" + add, this, showShell = false))
  }

  def setSettingValue(value: String): Unit = {
    semWrap(comm.command("set " + settingColon + ":=" + value, this, showShell = false))
  }

  private def semWrap(doStuff: => Unit) = {
    semaphore.down()
    doStuff
    semaphore.waitFor()
  }

  private val settingColon: String = taskName + (if (taskName.nonEmpty) ":" else "") + settingName

  override def apply(se: ShellEvent): Unit = se match {
    case TaskStart =>
    case TaskComplete => semaphore.up()
    case SbtShellCommunication.Output(output) => buffer.append(output)
  }
}