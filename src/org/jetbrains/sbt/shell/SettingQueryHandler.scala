package org.jetbrains.sbt.shell

import com.intellij.task.ProjectTaskResult

import scala.concurrent.ExecutionContext.Implicits.global
import org.jetbrains.sbt.shell.SbtShellCommunication.{EventAggregator, ShellEvent, TaskComplete, TaskStart}

import scala.concurrent.Future

class SettingQueryHandler(settingName: String, taskName: String, comm: SbtShellCommunication) {
  val defaultResult = new ProjectTaskResult(false, 0, 0)

  def getSettingValue(): Future[String] = {
    val listener = SettingQueryHandler.bufferedListener
    comm.command("show " + settingColon, defaultResult, listener, showShell = false).map {
      p: ProjectTaskResult => listener.getBufferedOutput
    }
  }

  def addToSettingValue(add: String): Future[Boolean] = {
    comm.command("set " + settingIn + "+=" + add, defaultResult, SettingQueryHandler.emptyListener, showShell = false).map {
      p: ProjectTaskResult => !p.isAborted && p.getErrors == 0
    }
  }

  def setSettingValue(value: String): Future[Boolean] = {
    comm.command("set " + settingIn + ":=" + value, defaultResult, SettingQueryHandler.emptyListener, showShell = false).map {
      p: ProjectTaskResult => !p.isAborted && p.getErrors == 0
    }
  }

  private val settingIn: String = settingName + (if (taskName.nonEmpty) " in " + taskName else "")
  private val settingColon: String = taskName + (if (taskName.nonEmpty) ":" else "") + settingName
}

object SettingQueryHandler {
  def apply(settingName: String, taskName: String, comm: SbtShellCommunication) =
    new SettingQueryHandler(settingName, taskName, comm)

  val emptyListener = new EventAggregator[ProjectTaskResult]() {
    override def apply(v1: ProjectTaskResult, v2: ShellEvent): ProjectTaskResult = v1
  }

  def bufferedListener = new EventAggregator[ProjectTaskResult]() {
    private val filterPrefix = "[info] "
    private val buffer = new StringBuilder()
    private var collectInfo = true

    def getBufferedOutput: String = buffer.mkString

    override def apply(res: ProjectTaskResult, se: ShellEvent): ProjectTaskResult = {
      se match {
        case TaskComplete =>
          collectInfo = false
        case SbtShellCommunication.Output(output) if collectInfo && output.startsWith(filterPrefix) =>
          buffer.append(output.stripPrefix(filterPrefix))
        case _ =>
      }
      res
    }
  }
}