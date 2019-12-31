package org.jetbrains.sbt.project.structure

import java.io.{BufferedWriter, File, OutputStreamWriter, PrintWriter}
import java.nio.charset.Charset
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

import com.intellij.build.events
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.MessageEvent.Kind._
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildMessages, ExternalSystemNotificationReporter}
import org.jetbrains.plugins.scala.findUsages.compilerReferences.compilation.SbtCompilationSupervisor
import org.jetbrains.plugins.scala.findUsages.compilerReferences.settings.CompilerIndicesSettings
import org.jetbrains.sbt.SbtUtil._
import org.jetbrains.sbt.project.SbtProjectResolver.ImportCancelledException
import org.jetbrains.sbt.project.structure.SbtStructureDump._
import org.jetbrains.sbt.shell.SbtShellCommunication
import org.jetbrains.sbt.shell.SbtShellCommunication._
import org.jetbrains.sbt.shell.event.SbtBuildEvent
import org.jetbrains.sbt.using

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class SbtStructureDump {

  private val cancellationFlag: AtomicBoolean = new AtomicBoolean(false)

  def cancel(): Unit = cancellationFlag.set(true)

  def dumpFromShell(taskId: ExternalSystemTaskId,
                    dir: File,
                    structureFilePath: String,
                    options: Seq[String],
                    notifications: ExternalSystemTaskNotificationListener,
                   ): Future[BuildMessages] = {

    notifications.onStart(taskId, dir.getCanonicalPath)

    val project = taskId.findProject() // assume responsibility of caller not to call dumpFromShell with null project
    val shell = SbtShellCommunication.forProject(project)

    val optString = options.mkString(" ")
    val setCmd = s"""set _root_.org.jetbrains.sbt.StructureKeys.sbtStructureOptions in Global := "$optString""""

    val ideaPortSetting =
      if (CompilerIndicesSettings(project).isBytecodeIndexingActive) {
        val ideaPort                    = SbtCompilationSupervisor().actualPort
        ideaPort.fold("")(port => s"; set ideaPort in Global := $port")
      } else ""

    val cmd = s";reload; $setCmd ;*/*:dumpStructureTo $structureFilePath; session clear-all $ideaPortSetting"
    val reporter = new ExternalSystemNotificationReporter(dir.getAbsolutePath, taskId, notifications)
    val aggregator = dumpMessageAggregator(taskId, EventId(s"dump:${UUID.randomUUID()}"), shell, reporter)

    shell.command(cmd, BuildMessages.empty, aggregator, showShell = false)
  }

  def dumpFromProcess(directory: File,
                      structureFilePath: String,
                      options: Seq[String],
                      vmExecutable: File,
                      vmOptions: Seq[String],
                      environment: Map[String, String],
                      sbtLauncher: File,
                      sbtStructureJar: File,
                      taskId: ExternalSystemTaskId,
                      notifications: ExternalSystemTaskNotificationListener
                     ): Try[BuildMessages] = {

    val optString = options.mkString(", ")

    val setCommands = Seq(
      """historyPath := None""",
      s"""shellPrompt := { _ => "" }""",
      s"""SettingKey[_root_.scala.Option[_root_.sbt.File]]("sbtStructureOutputFile") in _root_.sbt.Global := _root_.scala.Some(_root_.sbt.file("$structureFilePath"))""",
      s"""SettingKey[_root_.java.lang.String]("sbtStructureOptions") in _root_.sbt.Global := "$optString""""
    ).mkString("set _root_.scala.collection.Seq(", ",", ")")

    val sbtCommandArgs = ""

    val sbtCommands = Seq(
      setCommands,
      s"""apply -cp "${normalizePath(sbtStructureJar)}" org.jetbrains.sbt.CreateTasks""",
      s"*/*:dumpStructure"
    ).mkString(";", ";", "")

    val reporter = new ExternalSystemNotificationReporter(directory.getAbsolutePath, taskId, notifications)

    runSbt(
      directory, vmExecutable, vmOptions, environment,
      sbtLauncher, sbtCommandArgs, sbtCommands, reporter
    )
  }

  /** Run sbt with some sbt commands. */
  def runSbt(directory: File,
             vmExecutable: File,
             vmOptions: Seq[String],
             environment: Map[String, String],
             sbtLauncher: File,
             sbtCommandLineArgs: String,
             sbtCommands: String,
             reporter: ExternalSystemNotificationReporter
            ): Try[BuildMessages] = {

    val startTime = System.currentTimeMillis()
    // assuming here that this method might still be called without valid project

    val jvmOptions = SbtOpts.loadFrom(directory) ++ JvmOpts.loadFrom(directory) ++ vmOptions

    val processCommandsRaw =
      normalizePath(vmExecutable) +:
        "-Djline.terminal=jline.UnsupportedTerminal" +:
        "-Dsbt.log.noformat=true" +:
        "-Dfile.encoding=UTF-8" +:
        jvmOptions :+
        "-jar" :+
        normalizePath(sbtLauncher) :+
        sbtCommandLineArgs

    val processCommands = processCommandsRaw.filterNot(_.isEmpty)

    val dumpTaskId = EventId(s"dump:${UUID.randomUUID()}")
    // TODO message depends on who runs it
    reporter.startTask(dumpTaskId, None, "extracting build structure", System.currentTimeMillis())

    val result = Try {
      val processBuilder = new ProcessBuilder(processCommands.asJava)
      processBuilder.directory(directory)
      processBuilder.environment().putAll(environment.asJava)
      val process = processBuilder.start()
      val result = using(new PrintWriter(new BufferedWriter(new OutputStreamWriter(process.getOutputStream, "UTF-8")))) { writer =>
        writer.println(sbtCommands)
        // exit needs to be in a separate command, otherwise it will never execute when a previous command in the chain errors
        writer.println("exit")
        writer.flush()
        handle(process, dumpTaskId, reporter)
      }
      result.getOrElse(BuildMessages.empty.addError("no output from sbt shell process available"))
    }
    .recoverWith {
      case fail => Failure(ImportCancelledException(fail))
    }

    val eventResult = result match {
      case Success(messages) =>
        if (messages.errors.isEmpty)
          new events.impl.SuccessResultImpl(true)
        else {
          new events.impl.FailureResultImpl(messages.errors.asJava)
        }
      case Failure(x) =>
        new events.impl.FailureResultImpl(x)
    }

    reporter.finishTask(dumpTaskId, "structure exported", eventResult)

    result
  }

  private def handle(process: Process,
                     dumpTaskId: EventId,
                     reporter: ExternalSystemNotificationReporter
                    ): Try[BuildMessages] = {

    var messages = BuildMessages.empty

    def update(textRaw: String): Unit = {
      val text = textRaw.trim

      if (text.nonEmpty) {
        val msgEvent = buildEvent(text, SbtProcessBuildWarning(dumpTaskId, _), SbtProcessBuildError(dumpTaskId, _))
        msgEvent.foreach(reportEvent(messages, reporter, _))
        reporter.progressTask(dumpTaskId, 1, -1, "", "")
        reporter.log(text)
      }
    }

    val processListener: (OutputType, String) => Unit = {
      case (OutputType.StdOut, text) =>
        if (text.contains("(q)uit")) {
          val writer = new PrintWriter(process.getOutputStream)
          writer.println("q")
          writer.close()
        } else {
          update(text)
        }
      case (OutputType.StdErr, text) =>
        update(text)
    }

    Try {
      val handler = new OSProcessHandler(process, "sbt import", Charset.forName("UTF-8"))
      handler.addProcessListener(new ListenerAdapter(processListener))
      handler.startNotify()

      var processEnded = false
      while (!processEnded && !cancellationFlag.get())
        processEnded = handler.waitFor(SBT_PROCESS_CHECK_TIMEOUT_MSEC)

      if (!processEnded) {
        // task was cancelled
        handler.setShouldDestroyProcessRecursively(false)
        handler.destroyProcess()
        throw ImportCancelledException(new Exception("task canceled"))
      } else messages
    }
  }
}

object SbtStructureDump {

  private val SBT_PROCESS_CHECK_TIMEOUT_MSEC = 100

  private val WARN_PREFIX = "[warn]"
  private val ERROR_PREFIX = "[error]"

  private def buildEvent(text: String, toWarning: String => MessageEvent, toError: String => MessageEvent): Option[MessageEvent] =
    if (text.startsWith("[warn]")) {
      val strippedText = text.stripPrefix(WARN_PREFIX).trim
      Option(toWarning(strippedText))
    } else if (text.startsWith("[error]")) {
      val strippedText = text.stripPrefix(ERROR_PREFIX).trim
      Option(toError(strippedText))
    } else None

  private def reportEvent(messages: BuildMessages,
                          reporter: ExternalSystemNotificationReporter,
                          event: MessageEvent): BuildMessages = {
    event.getKind match {
      case WARNING =>
        // report only that there is an error, until we can parse output more precisely
        if (messages.warnings.isEmpty) {
          reporter.warning("error during import", None)
        }
        messages.addWarning(event.getMessage)
      case ERROR =>
        // report only that there is an error, until we can parse output more precisely
        if (messages.errors.isEmpty) {
          reporter.error("error during import", None)
        }
        messages.addError(event.getMessage)
      case INFO | SIMPLE | STATISTICS =>
        messages
    }
  }

  private def dumpMessageAggregator(id: ExternalSystemTaskId,
                                    dumpTaskId: EventId,
                                    shell: SbtShellCommunication,
                                    reporter: ExternalSystemNotificationReporter,
                                   ): EventAggregator[BuildMessages] = {
    case (messages, TaskStart) =>
      reporter.startTask(dumpTaskId, None, "extracting structure")
      messages

    case (messages, TaskComplete) =>
      reporter.finish(messages)
      messages

    case (messages, ErrorWaitForInput) =>
      val msg = "import errors, project reload aborted"
      val ex = new ExternalSystemException(msg)

      val result = new com.intellij.build.events.impl.FailureResultImpl(msg, ex)
      reporter.finishTask(dumpTaskId, msg, result)
      reporter.error(msg, None)

      shell.send("i" + System.lineSeparator)

      messages.addError(msg)

    case (messages, Output(raw)) =>
      val text = raw.trim

      reporter.progressTask(dumpTaskId, 1, -1, "events", text)
      reporter.log(text)

      messages
  }


  sealed trait ImportType
  case object ShellImport extends ImportType
  case object ProcessImport extends ImportType

  trait SbtProcessBuildEvent extends MessageEvent {
    // TODO open log or something?
    override def getNavigatable(project: Project): Navigatable = null
  }

  case class SbtProcessBuildWarning(parentId: Any, message: String)
    extends SbtBuildEvent(parentId, MessageEvent.Kind.WARNING, "warnings", message) with SbtProcessBuildEvent

  case class SbtProcessBuildError(parentId: Any, message: String)
    extends SbtBuildEvent(parentId, MessageEvent.Kind.ERROR, "errors", message) with SbtProcessBuildEvent

}
