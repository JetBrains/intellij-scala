package org.jetbrains.sbt.shell

import com.intellij.task.ProjectTaskResult
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration.SettingEntry
import org.jetbrains.sbt.shell.SbtShellCommunication.{EventAggregator, ShellEvent, TaskComplete}
import org.jetbrains.sbt.shell.SettingQueryHandler._

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SettingQueryHandler private(settingName: String,
                                  taskName: Option[String],
                                  sbtProjectUri: Option[String],
                                  sbtProjectName: Option[String],
                                  comm: SbtShellCommunication) {
  private val defaultResult = new ProjectTaskResult(false, 0, 0)

  def fetchSettingValue(): Future[String] = {
    val listener = new BufferedListener(this)
    for {
      _ <- comm.command(s"show $settingColon", defaultResult, listener, showShell = false)
    } yield filterSettingValue(listener.getBufferedOutput)
  }

  def addToSettingValue(add: String): Future[Boolean] =
    for {
      result <- comm.command(s"set $settingIn+=$add", defaultResult, EmptyListener, showShell = false)
    } yield !result.isAborted && result.getErrors == 0

  def setSettingValue(value: String): Future[Boolean] =
    for {
      result <- comm.command(s"set $settingIn:=$value", defaultResult, EmptyListener, showShell = false)
    } yield !result.isAborted && result.getErrors == 0

  private val settingIn: String = (sbtProjectUri, sbtProjectName) match {
    case (Some(uri), Some(project)) =>
      def quoted(s: String): String = '"' + s + '"'
      s"$settingName.in(ProjectRef(uri(${quoted(uri)}), ${quoted(project)}))${taskName.map(" in " + _).getOrElse("")}"
    case (None, Some(project)) =>
      s"$settingName in $project${taskName.map(" in " + _).getOrElse("")}"
    case _ =>
      settingName
  }

  private val settingColon: String =
    getProjectIdPrefix(sbtProjectUri, sbtProjectName) + taskName.fold("*:")(_ + ":") + settingName

  private val settingValuePrefixes: Seq[String] = List(
    getProjectIdPrefix(None, sbtProjectName) + taskName.fold("")(_ + ":") + settingName,
    getProjectIdPrefix(None, sbtProjectName) + "*" + taskName.fold("")(_ + ":") + settingName
  )

  private def filterSettingValue(value: String): String = settingName match {
    case "testOptions" | "javaOptions" if value.trim.startsWith("*") => //13.13 notation
      val values      = value.split("\n")
      val valuesFixed = values.map(_.trim.stripPrefix("* "))
      s"List(${valuesFixed.mkString(", ")})"
    case _                                                             =>
      value
  }
}

object SettingQueryHandler {

  def apply(settingName: String, taskName: Option[String], sbtProjectUri: Option[String],
            sbtProjectName: Option[String], comm: SbtShellCommunication) =
    new SettingQueryHandler(settingName, taskName, sbtProjectUri, sbtProjectName, comm)

  def apply(settingsEntry: SettingEntry, comm: SbtShellCommunication) =
    new SettingQueryHandler(settingsEntry.settingName, settingsEntry.task, settingsEntry. sbtProjectUri, settingsEntry.sbtProjectId, comm)

  private val EmptyListener: EventAggregator[ProjectTaskResult] = (v1: ProjectTaskResult, _: ShellEvent) => v1

  private class BufferedListener(handler: SettingQueryHandler) extends EventAggregator[ProjectTaskResult] {
    private val filterPrefix = "[info] "
    private val successPrefix = "[success] "
    private var strings = ListBuffer[String]()
    private var collectInfo = true

    def getBufferedOutput: String = {
      strings = strings.dropWhile { line =>
        !line.startsWith(filterPrefix) && !handler.settingValuePrefixes.contains(line.stripPrefix(filterPrefix))
      }

      if (strings.isEmpty) {
        ""
      } else if (strings.length == 1) {
        strings.head.stripPrefix(filterPrefix)
      } else {
        strings.find(handler.settingValuePrefixes.contains) match {
          case Some(prefix) => //for sbt 13.12 and less
            strings(strings.indexOf(prefix) + 1)
          case None => //for sbt 13.13
            strings = strings.map(_.stripPrefix(filterPrefix) + "\n")
            val lines = strings.takeWhile { line =>
              !line.startsWith(filterPrefix) && !line.startsWith(successPrefix) && line.trim != "*"
            }
            lines.mkString
        }
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

  def getProjectIdPrefix(uri: Option[String], project: Option[String]): String =
    uri.map("{" + _ + "}").getOrElse("") + project.map(_ + "/").getOrElse("")
}