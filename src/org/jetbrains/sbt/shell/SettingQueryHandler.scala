package org.jetbrains.sbt.shell

import com.intellij.task.ProjectTaskResult

import scala.concurrent.ExecutionContext.Implicits.global
import org.jetbrains.sbt.shell.SbtShellCommunication.{EventAggregator, ShellEvent, TaskComplete, TaskStart}

import scala.concurrent.Future

case class SettingQueryHandler(settingName: String, taskName: String, comm: SbtShellCommunication) extends EventAggregator[ProjectTaskResult] {
  private val buffer = new StringBuilder()

  val defaultResult = new ProjectTaskResult(false, 0, 0)

  def getSettingValue(): Future[String] = {
    comm.command("show " + settingColon , defaultResult, this, showShell = false).flatMap {
      p: ProjectTaskResult => Future(buffer.mkString)
    }
  }

  def addToSettingValue(add: String): Future[Boolean] = {
    comm.command("set " + settingIn + "+=" + add, defaultResult, this, showShell = false).flatMap {
      p: ProjectTaskResult => Future(!p.isAborted && p.getErrors == 0)
    }
  }

  def setSettingValue(value: String): Future[Boolean] = {
    comm.command("set " + settingIn + ":=" + value, defaultResult, this, showShell = false).flatMap {
      p: ProjectTaskResult => Future(!p.isAborted && p.getErrors == 0)
    }
  }

  private val settingIn: String = settingName + (if (taskName.nonEmpty) " in " + taskName else "")
  private val settingColon: String = taskName + (if (taskName.nonEmpty) ":" else "") + settingName

  override def apply(res: ProjectTaskResult, se: ShellEvent): ProjectTaskResult = {
    se match {
      case TaskStart =>
      case TaskComplete =>
      case SbtShellCommunication.Output(output) => buffer.append(output)
    }
    res
  }
}