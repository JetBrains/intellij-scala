package org.jetbrains.sbt.shell

import com.intellij.task.{ProjectTaskContext, ProjectTaskManager}
import org.jetbrains.plugins.scala.build.TaskManagerResult
import org.jetbrains.sbt.SbtUtil.SbtProjectUriAndId
import org.jetbrains.sbt.SbtVersionCapabilities
import org.jetbrains.sbt.shell.SbtShellCommunication.{EventAggregator, ShellEvent, TaskComplete}
import org.jetbrains.sbt.shell.SettingQueryHandler._

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// TODO replace this when we have sbt server support
/**
 * NOTE: This class is only used to edit the test settings
 *
 * @param settingName for example, "scalacOptions"
 */
class SettingQueryHandler private[jetbrains] (
  sbtProjectUriAndId: Option[SbtProjectUriAndId],
  settingName: String,
  comm: SbtShellCommunication,
) {
  private val sbtVersion = comm.getRunningOrDetectedSbtVersion
  private val useSlash = SbtVersionCapabilities.isSlashSyntaxSupported(sbtVersion)

  // In older sbt you had to use the lower-case version in "show" even if upper-case was used in set command
  // set scalaVersion in Test in root := "1.2.3"
  // show root/test:scalaVersion
  private val TestConfig = "Test"
  private val scopeForShow = if (useSlash) TestConfig else "test"
  private val scopeForSet = TestConfig

  def getSettingValue: Future[String] = {
    val command = s"show $settingColon"

    val listener = new BufferedListener(this)
    comm.command(command, DefaultResult, listener).map {
      (_: Result) => filterSettingValue(listener.getBufferedOutput)
    }
  }

  def addToSettingValue(add: String): Future[Boolean] = {
    val command = s"set $settingPathForSetCommand += $add"
    comm.command(command, DefaultResult, EmptyListener).map {
      (p: Result) => !p.isAborted && !p.hasErrors
    }
  }

  def setSettingValue(value: String): Future[Boolean] = {
    val command = s"set $settingPathForSetCommand := $value"
    comm.command(command, DefaultResult, EmptyListener).map {
      (p: Result) => !p.isAborted && !p.hasErrors
    }
  }

  private val settingPathForSetCommand: String = {
    val scoped = sbtProjectUriAndId.map(buildScopedSettingTextForSetCommand)
    scoped.getOrElse(settingName)
  }

  private def buildScopedSettingTextForSetCommand(udiAndId: SbtProjectUriAndId): String = {
    val projectRefOrName = s"ProjectRef(uri(${quoted(udiAndId.uri)}), ${quoted(udiAndId.id)})"
    buildScopedSettingTextForSetCommand(projectRefOrName, scopeForSet, settingName)
  }

  private def buildScopedSettingTextForSetCommand(projectNameOrRef: String, taskName: String, settingName: String): String =
    if (useSlash) // set project / task / setting := ...
      s"$projectNameOrRef/$taskName/$settingName"
    else  // set setting.in(project) in task  := ...
      s"$settingName.in($projectNameOrRef).in($taskName)"

  //## SBT 0.13.x and earlier
  //```
  //show myproject/test:parallelExecution
  //show myproject/compile:scalaVersion
  //show myproject/it:libraryDependencies
  //show {file:/path/to/project/}myproject/test:parallelExecution
  //```
  //
  //## SBT 1.5.0+ (Modern Syntax - Old Deprecated)
  //```
  //show Test / parallelExecution
  //show Compile / scalaVersion
  //show IntegrationTest / libraryDependencies
  //show root / Test / parallelExecution
  //show mysubproject / Compile / scalacOptions
  //```
  private val taskScopedSettingForShow = if (useSlash)
    s"$scopeForShow/$settingName"
  else
    s"$scopeForShow:$settingName"

  private val settingColon: String = {
    val projectPrefix = SettingQueryHandler.getProjectIdPrefix(sbtProjectUriAndId)
    projectPrefix + taskScopedSettingForShow
  }

  private val settingValuePrefixes: Seq[String] = {
    val projectPrefix: String = SettingQueryHandler.getProjectIdPrefix(None, sbtProjectUriAndId.map(_.id))
    List(
      projectPrefix + taskScopedSettingForShow + settingName,
      projectPrefix + "*" + taskScopedSettingForShow + settingName
    )
  }

  private def filterSettingValue(in: String): String =
    settingName match {
      case "testOptions" | "javaOptions" if in.trim.startsWith("*") => // 13.13 notation
        s"List(${in.split("\n").map(_.trim.stripPrefix("* ")).mkString(", ")})"
      case _ => in
    }
}

object SettingQueryHandler {

  private type Result = ProjectTaskManager.Result

  private val DefaultResult: TaskManagerResult = TaskManagerResult(new ProjectTaskContext(), isAborted = false, hasErrors = false)

  private val EmptyListener: EventAggregator[Result] = (v1: Result, _: ShellEvent) => v1

  private class BufferedListener(handler: SettingQueryHandler) extends EventAggregator[Result]() {
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

    override def apply(res: Result, se: ShellEvent): Result = {
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

  private def getProjectIdPrefix(sbtProjectUriAndId: Option[SbtProjectUriAndId]): String =
    sbtProjectUriAndId.map(getProjectIdPrefix).getOrElse("")

  def getProjectIdPrefix(sbtProjectUriAndId: SbtProjectUriAndId): String =
    getProjectIdPrefix(Some(sbtProjectUriAndId.uri), Some(sbtProjectUriAndId.id))

  // Examples:
  //  - {projectUri}projectName/
  //  - projectName/
  //  - {projectUri}
  def getProjectIdPrefix(uri: Option[String], project: Option[String]): String = {
    val projectUriPart = uri.map("{" + _ + "}").getOrElse("")
    val projectIdPart = project.map(_ + "/").getOrElse("")
    projectUriPart + projectIdPart
  }

  private def quoted(s: String): String = "\"" + s + "\""
}
