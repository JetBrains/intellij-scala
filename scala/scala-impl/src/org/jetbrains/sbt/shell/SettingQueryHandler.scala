package org.jetbrains.sbt.shell

import com.intellij.task.ProjectTaskResult
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration.SettingEntry

import scala.concurrent.ExecutionContext.Implicits.global
import org.jetbrains.sbt.shell.SbtShellCommunication.{EventAggregator, ShellEvent, TaskComplete}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

class SettingQueryHandler private (settingName: String, taskName: Option[String], sbtProjectUri: Option[String],
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
      s"$settingName.in(ProjectRef(uri(${quoted(uri)}), ${quoted(project)}))${taskName.map(" in " + _).getOrElse("")}"
    case (None, Some(project)) =>
      s"$settingName in $project${taskName.map(" in " + _).getOrElse("")}"
    case _ => settingName
  }

  private val settingColon: String =
    SettingQueryHandler.getProjectIdPrefix(sbtProjectUri, sbtProjectName) + taskName.map(_ + ":").getOrElse("*:") +
      settingName
  private val settingValuePrefixes: Seq[String] =
    List(SettingQueryHandler.getProjectIdPrefix(None, sbtProjectName) + taskName.map(_ + ":").getOrElse("") + settingName,
      SettingQueryHandler.getProjectIdPrefix(None, sbtProjectName) + "*" + taskName.map(_ + ":").getOrElse("") + settingName)

  def filterSettingValue(in: String): String = {
    settingName match {
      case ("testOptions" | "javaOptions") if in.trim.startsWith("*") => //13.13 notation
        s"List(${in.split("\n").map(_.trim.stripPrefix("* ")).mkString(", ")})"
      case _ => in
    }
  }
}

object SettingQueryHandler {
  def apply(settingName: String, taskName: Option[String], sbtProjectUri: Option[String],
            sbtProjectName: Option[String], comm: SbtShellCommunication) =
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
    private var strings = ListBuffer[String]()
    private var collectInfo = true

    def getBufferedOutput: String = {
      strings = strings.dropWhile(line => !line.startsWith(filterPrefix) && !handler.settingValuePrefixes.contains(line.stripPrefix(filterPrefix)))
      if (strings.isEmpty) return ""
      if (strings.length == 1) return strings.head.stripPrefix(filterPrefix)
      strings.find(handler.settingValuePrefixes.contains) match {
        case Some(prefix) => //for sbt 13.12 and less
          strings(strings.indexOf(prefix) + 1)
        case None => //for sbt 13.13
          strings = strings.map(_.stripPrefix(filterPrefix) + "\n")
          val res = new StringBuilder()
          strings.takeWhile(line => !line.startsWith(filterPrefix) && !line.startsWith(successPrefix) && line.trim != "*").foreach(res.append)
          res.mkString
      }
    }

    override def apply(res: ProjectTaskResult, se: ShellEvent): ProjectTaskResult = {
      se match {
        case TaskComplete =>
          collectInfo = false
        case SbtShellCommunication.Output(output) if collectInfo =>
          strings += output
        case _ =>
      }
      res
    }
  }

  def getProjectIdPrefix(uriAndProject: (Option[String], Option[String])): String =
    getProjectIdPrefix(uriAndProject._1, uriAndProject._2)
  def getProjectIdPrefix(uri: Option[String], project: Option[String]): String =
    uri.map("{" + _ + "}").getOrElse("") + project.map(_ + "/").getOrElse("")
}