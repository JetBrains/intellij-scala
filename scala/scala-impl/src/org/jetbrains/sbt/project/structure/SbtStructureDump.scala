package org.jetbrains.sbt.project.structure

import java.io.{BufferedWriter, File, OutputStreamWriter, PrintWriter}
import java.nio.charset.Charset
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.{FailureResultImpl, SkippedResultImpl, SuccessResultImpl}
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.{Nls, NonNls}
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter, ExternalSystemNotificationReporter}
import org.jetbrains.plugins.scala.findUsages.compilerReferences.compilation.SbtCompilationSupervisor
import org.jetbrains.plugins.scala.findUsages.compilerReferences.settings.CompilerIndicesSettings
import org.jetbrains.sbt.SbtUtil._
import org.jetbrains.sbt.project.SbtProjectResolver.ImportCancelledException
import org.jetbrains.sbt.project.structure.SbtStructureDump._
import org.jetbrains.sbt.shell.SbtShellCommunication
import org.jetbrains.sbt.shell.SbtShellCommunication._
import org.jetbrains.sbt.shell.event.SbtBuildEvent
import org.jetbrains.sbt.{SbtBundle, using}

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class SbtStructureDump {

  private val cancellationFlag: AtomicBoolean = new AtomicBoolean(false)

  def cancel(): Unit = cancellationFlag.set(true)

  def dumpFromShell(project: Project,
                    structureFilePath: String,
                    options: Seq[String],
                    reporter: BuildReporter
                   ): Future[BuildMessages] = {

    reporter.start()

    val shell = SbtShellCommunication.forProject(project)

    val optString = options.mkString(" ")
    val setCmd = s"""set _root_.org.jetbrains.sbt.StructureKeys.sbtStructureOptions in Global := "$optString""""

    val ideaPortSetting =
      if (CompilerIndicesSettings(project).isBytecodeIndexingActive) {
        val ideaPort                    = SbtCompilationSupervisor().actualPort
        ideaPort.fold("")(port => s"; set ideaPort in Global := $port")
      } else ""

    val cmd = s";reload; $setCmd ;*/*:dumpStructureTo $structureFilePath; session clear-all $ideaPortSetting"
    val aggregator = shellMessageAggregator(EventId(s"dump:${UUID.randomUUID()}"), shell, reporter)

    shell.command(cmd, BuildMessages.empty, aggregator)
  }

  def dumpFromProcess(directory: File,
                      structureFilePath: String,
                      options: Seq[String],
                      vmExecutable: File,
                      vmOptions: Seq[String],
                      environment: Map[String, String],
                      sbtLauncher: File,
                      sbtStructureJar: File,
                      reporter: BuildReporter
                     ): Try[BuildMessages] = {

    val optString = options.mkString(", ")

    val setCommands = Seq(
      """historyPath := None""",
      s"""shellPrompt := { _ => "" }""",
      s"""SettingKey[_root_.scala.Option[_root_.sbt.File]]("sbtStructureOutputFile") in _root_.sbt.Global := _root_.scala.Some(_root_.sbt.file("$structureFilePath"))""",
      s"""SettingKey[_root_.java.lang.String]("sbtStructureOptions") in _root_.sbt.Global := "$optString""""
    ).mkString("set _root_.scala.collection.Seq(", ",", ")")

    val sbtCommandArgs = List.empty

    val sbtCommands = Seq(
      setCommands,
      s"""apply -cp "${normalizePath(sbtStructureJar)}" org.jetbrains.sbt.CreateTasks""",
      s"*/*:dumpStructure"
    ).mkString(";", ";", "")


    runSbt(
      directory, vmExecutable, vmOptions, environment,
      sbtLauncher, sbtCommandArgs, sbtCommands, reporter,
      SbtBundle.message("sbt.extracting.project.structure.from.sbt")
    )
  }

  /** Run sbt with some sbt commands. */
  def runSbt(directory: File,
             vmExecutable: File,
             vmOptions: Seq[String],
             environment: Map[String, String],
             sbtLauncher: File,
             sbtCommandLineArgs: List[String],
             @NonNls sbtCommands: String,
             reporter: BuildReporter,
             @Nls reportMessage: String,
            ): Try[BuildMessages] = {

    val startTime = System.currentTimeMillis()
    // assuming here that this method might still be called without valid project

    val jvmOptions = SbtOpts.loadFrom(directory) ++ JvmOpts.loadFrom(directory) ++ vmOptions

    val processCommandsRaw =
      List(
        normalizePath(vmExecutable),
        "-Djline.terminal=jline.UnsupportedTerminal",
        "-Dsbt.log.noformat=true",
        "-Dfile.encoding=UTF-8") ++
      jvmOptions ++
      List("-jar", normalizePath(sbtLauncher)) ++
      sbtCommandLineArgs

    val processCommands = processCommandsRaw.filterNot(_.isEmpty)

    val dumpTaskId = EventId(s"dump:${UUID.randomUUID()}")
    reporter.startTask(dumpTaskId, None, reportMessage, startTime)

    val resultMessages = Try {
      val processBuilder = new ProcessBuilder(processCommands.asJava)
      processBuilder.directory(directory)
      processBuilder.environment().putAll(environment.asJava)
      val procString = processBuilder.command().asScala.mkString(" ")
      reporter.log(procString)

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

    val eventResult = resultMessages match {
      case Success(messages) =>
        messages.status match {
          case BuildMessages.OK =>
            new SuccessResultImpl(true)
          case BuildMessages.Canceled =>
            new SkippedResultImpl()
          case BuildMessages.Error | BuildMessages.Indeterminate =>
            new FailureResultImpl(messages.errors.asJava)
        }

      case Failure(x) =>
        new FailureResultImpl(x)
    }

    reporter.finishTask(dumpTaskId, reportMessage, eventResult)

    resultMessages
  }

  private def handle(process: Process,
                     dumpTaskId: EventId,
                     reporter: BuildReporter
                    ): Try[BuildMessages] = {

    var messages = BuildMessages.empty

   def update(typ: OutputType, textRaw: String): Unit = {
      val text = textRaw.trim

      if (text.nonEmpty) {
        messages = reportEvent(messages, reporter, text)
        reporter.progressTask(dumpTaskId, 1, -1, "", text)
        (typ, reporter) match {
          case (OutputType.StdErr, reporter: ExternalSystemNotificationReporter) =>
            reporter.logErr(text)
          case _ => reporter.log(text)
        }
      }
    }

    val processListener: (OutputType, String) => Unit = {
      case (typ@OutputType.StdOut, text) =>
        if (text.contains("(q)uit")) {
          val writer = new PrintWriter(process.getOutputStream)
          writer.println("q")
          writer.close()
        } else {
          update(typ, text)
        }
      case (typ@OutputType.StdErr, text) =>
        update(typ, text)
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
      } else if (handler.getExitCode != 0)
          messages.status(BuildMessages.Error)
      else if (messages.status == BuildMessages.Indeterminate)
        messages.status(BuildMessages.OK)
      else messages
    }
  }
}

object SbtStructureDump {

  private val SBT_PROCESS_CHECK_TIMEOUT_MSEC = 100

  private def reportEvent(messages: BuildMessages,
                          reporter: BuildReporter,
                          text: String): BuildMessages = {

    if (text.startsWith("[error] Total time")) {
      val msg = SbtBundle.message("sbt.task.failed.see.log.for.details")
      reporter.error(msg, None)
      messages
        .addError(msg)
        .status(BuildMessages.Error)
    } else messages
  }

  private def shellMessageAggregator(dumpTaskId: EventId,
                                     shell: SbtShellCommunication,
                                     reporter: BuildReporter,
                                   ): EventAggregator[BuildMessages] = {
    case (messages, TaskStart) =>
      reporter.startTask(dumpTaskId, None, SbtBundle.message("sbt.extracting.project.structure.from.sbt.shell"))
      messages

    case (messages, TaskComplete) =>
      reporter.finishTask(dumpTaskId, SbtBundle.message("sbt.project.structure.extracted"), new SuccessResultImpl())
      val messagesUpdated =
        if (messages.status == BuildMessages.Indeterminate) messages.status(BuildMessages.OK)
        else messages
      messagesUpdated

    case (messages, ErrorWaitForInput) =>
      val msg = SbtBundle.message("sbt.import.errors.project.reload.aborted")
      val ex = new ExternalSystemException(msg)

      val result = new FailureResultImpl(msg, ex)
      reporter.finishTask(dumpTaskId, msg, result)

      shell.send("i" + System.lineSeparator)

      messages.addError(msg)

    case (messages, Output(raw)) =>
      val text = raw.trim

      reporter.progressTask(dumpTaskId, 1, -1, SbtBundle.message("sbt.events"), text)
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
