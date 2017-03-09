package org.jetbrains.sbt.shell

import com.intellij.task.ProjectTaskResult
import scala.concurrent.ExecutionContext.Implicits.global
import org.jetbrains.sbt.shell.SbtShellCommunication.{ShellEvent, TaskComplete, TaskStart}

import scala.concurrent.Future

case class SettingQueryHandler(settingName: String, taskName: String, comm: SbtShellCommunication) extends SbtShellCommunication.EventHandler {
  private val buffer = new StringBuilder()

  def getSettingValue(): Future[String] = {
    comm.command("show " + settingColon , this, showShell = false).flatMap {
      p: ProjectTaskResult => Future(buffer.mkString)
    }
  }

  def addToSettingValue(add: String): Future[Boolean] = {
    comm.command("set " + settingIn + "+=" + add, this, showShell = false).flatMap {
      p: ProjectTaskResult => Future(!p.isAborted && p.getErrors == 0)
    }
  }

  def setSettingValue(value: String): Future[Boolean] = {
    comm.command("set " + settingIn + ":=" + value, this, showShell = false).flatMap {
      p: ProjectTaskResult => Future(!p.isAborted && p.getErrors == 0)
    }
  }

  private val settingIn: String = settingName + (if (taskName.nonEmpty) " in " + taskName else "")
  private val settingColon: String = taskName + (if (taskName.nonEmpty) ":" else "") + settingName

  override def apply(se: ShellEvent): Unit = se match {
    case TaskStart =>
    case TaskComplete =>
    case SbtShellCommunication.Output(output) => buffer.append(output)
  }
}