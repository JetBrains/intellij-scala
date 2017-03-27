package org.jetbrains.sbt.shell

import com.intellij.task.ProjectTaskResult
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration.SettingEntry

import scala.concurrent.ExecutionContext.Implicits.global
import org.jetbrains.sbt.shell.SbtShellCommunication.{EventAggregator, ShellEvent, TaskComplete}

import scala.concurrent.Future

class SettingQueryHandler private (settingName: String, taskName: String, sbtProjectUri: Option[String],
                                   sbtProjectName: Option[String], comm: SbtShellCommunication) {
  val defaultResult = new ProjectTaskResult(false, 0, 0)

  def getSettingValue(): Future[String] = {
    val listener = SettingQueryHandler.bufferedListener(this)
    comm.command("show " + settingColon, defaultResult, listener, showShell = false).map {
      _: ProjectTaskResult => filterSettingValue(listener.getBufferedOutput)
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

  private val settingIn: String = (sbtProjectUri, sbtProjectName) match {
    case (Some(uri), Some(project)) =>
      def quoted(s: String): String = '"' + s + '"'
      s"$settingName.in(ProjectRef(uri(${quoted(uri)}), ${quoted(project)})) in $taskName"
    case _ => settingName
  }

    settingName + sbtProjectName.map(".in( \"" + _ + "\")").getOrElse("") + " in " + taskName
  private val settingColon: String = SettingQueryHandler.getProjectIdPrefix(sbtProjectUri, sbtProjectName) + taskName + ":" + settingName
  private val settingColonNoUri: String = SettingQueryHandler.getProjectIdPrefix(None, sbtProjectName) + taskName + ":" + settingName

  def filterSettingValue(in: String): String = {
    settingName match {
      case "testOptions" if in.trim() == "*" =>  "List()"
      case _ => in
    }
  }
}

object SettingQueryHandler {
  def apply(settingName: String, taskName: String, sbtProjectUri: Option[String], sbtProjectName: Option[String], comm: SbtShellCommunication) =
    new SettingQueryHandler(settingName, taskName, sbtProjectUri, sbtProjectName, comm)

  def apply(settingsEntry: SettingEntry, comm: SbtShellCommunication) =
    new SettingQueryHandler(settingsEntry.settingName, settingsEntry.task, settingsEntry. sbtProjectUri,
      settingsEntry.sbtProjectId, comm)

  val emptyListener = new EventAggregator[ProjectTaskResult]() {
    override def apply(v1: ProjectTaskResult, v2: ShellEvent): ProjectTaskResult = v1
  }

  def bufferedListener(handler: SettingQueryHandler) = new EventAggregator[ProjectTaskResult]() {
    private val filterPrefix = "[info] "
    private val successPrefix = "[success] "
    private val buffer = new StringBuilder()

    private final val DO_NOT_COLLECT = 0
    private final val FIRST_LINE = 1
    private final val OTHER_LINES = 2

    private var collectInfo: Int = DO_NOT_COLLECT

    def getBufferedOutput: String = {
      val res = buffer.mkString
      println(s"Collected ${handler.settingColon} :  $res")
      res
    }

    override def apply(res: ProjectTaskResult, se: ShellEvent): ProjectTaskResult = {
      se match {
        case TaskComplete =>
          collectInfo = DO_NOT_COLLECT
        case SbtShellCommunication.Output(output) =>
          println(s"Collecting ${handler.settingColon} :  $output ")
          if (output.startsWith(filterPrefix) && output.stripPrefix(filterPrefix) == handler.settingColonNoUri) {
            collectInfo = FIRST_LINE
          } else if (collectInfo == FIRST_LINE && output.startsWith(filterPrefix)) {
            buffer.append(output.stripPrefix(filterPrefix))
            collectInfo = OTHER_LINES
          } else if (collectInfo == OTHER_LINES && !output.startsWith(filterPrefix) && !output.startsWith(successPrefix)) {
            buffer.append(output)
          } else {
            collectInfo = DO_NOT_COLLECT
          }
        case _ =>
      }
      res
    }
  }

  def getProjectIdPrefix(uriAndProject: (Option[String], Option[String])): String = getProjectIdPrefix(uriAndProject._1, uriAndProject._2)
  def getProjectIdPrefix(uri: Option[String], project: Option[String]): String =
    uri.map("{" + _ + "}").getOrElse("") + project.map(_ + "/").getOrElse("")
}