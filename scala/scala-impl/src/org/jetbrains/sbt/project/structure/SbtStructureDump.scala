package org.jetbrains.sbt.project.structure

import com.intellij.build.events.impl.{FailureResultImpl, SkippedResultImpl, SuccessResultImpl}
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.{Nls, NonNls}
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter, ExternalSystemNotificationReporter}
import org.jetbrains.plugins.scala.extensions.LoggerExt
import org.jetbrains.plugins.scala.findUsages.compilerReferences.compilation.SbtCompilationSupervisor
import org.jetbrains.plugins.scala.findUsages.compilerReferences.settings.CompilerIndicesSettings
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.SbtUtil._
import org.jetbrains.sbt.project.SbtProjectResolver.ImportCancelledException
import org.jetbrains.sbt.project.structure.SbtStructureDump._
import org.jetbrains.sbt.shell.SbtShellCommunication
import org.jetbrains.sbt.shell.SbtShellCommunication._

import java.io.{BufferedWriter, File, OutputStreamWriter, PrintWriter}
import java.nio.charset.Charset
import java.util.UUID
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try, Using}

class SbtStructureDump {

  private val cancellationFlag: AtomicBoolean = new AtomicBoolean(false)

  // NOTE: if this is a first run of sbt with a particular version on current machine
  // sbt import will take some time because it will have to download quite a lot of dependencies
  private val MaxImportDurationInUnitTests: FiniteDuration = 10.minutes

  // in failed tests we would like to see sbt process output
  private val processOutputBuilder = new StringBuilder
  def processOutput: String = processOutputBuilder.mkString

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
                     )
                     (implicit reporter: BuildReporter)
  : Try[BuildMessages] = {

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
      sbtLauncher, sbtCommandArgs, sbtCommands,
      SbtBundle.message("sbt.extracting.project.structure.from.sbt")
    )
  }

  /**
   * This is a workaround for [[https://github.com/sbt/sbt/issues/5128]] (tested for sbt 1.4.9)
   *
   * The bug is reproduced on Teamcity, on Windows agents:
   * ProjectImportingTest is stuck indefinitely when the test is run from sbt.<br>
   * It's also reproduces locally when running the test from sbt.<br>
   * But for some reason is not reproduced when running from IDEA test runners<br>
   *
   * Environment variables which have to be mocked are inferred from methods in
   * `lmcoursier.internal.shaded.coursier.paths.CoursierPaths` (version 2.0.6)
   *
   * @see [[https://github.com/sbt/sbt/issues/5128]]
   * @see [[https://github.com/dirs-dev/directories-jvm/issues/49]]
   * @see [[https://github.com/ScoopInstaller/Main/pull/878/files]]
   */
  private def defaultCoursierDirectoriesAsEnvVariables(): Seq[(String, String)] = {
    val LocalAppData = System.getenv("LOCALAPPDATA")
    val AppData = System.getenv("APPDATA")

    val CoursierLocalAppDataHome = LocalAppData + "/Coursier"
    val CoursierAppDataHome = AppData + "/Coursier"

    Seq(
      // these 2 variables seems to be enough for the workaround
      ("COURSIER_CACHE", CoursierLocalAppDataHome + "/cache/v1"),
      ("COURSIER_CONFIG_DIR", CoursierAppDataHome + "/config"),
      // these 2 variables seems to be optional, but we set them just in cause
      // they might be accessed in some unpredictable cases
      ("COURSIER_JVM_CACHE", CoursierLocalAppDataHome + "/cache/jvm"),
      ("COURSIER_DATA_DIR", CoursierLocalAppDataHome + "/data"),
      // this also looks like an optional in 1.4.9, but setting it just in case
      ("COURSIER_HOME", CoursierLocalAppDataHome),
    )
  }

  /** Run sbt with some sbt commands. */
  def runSbt(directory: File,
             vmExecutable: File,
             vmOptions: Seq[String],
             environment0: Map[String, String],
             sbtLauncher: File,
             sbtCommandLineArgs: List[String],
             @NonNls sbtCommands: String,
             @Nls reportMessage: String,
            )
            (implicit reporter: BuildReporter)
  : Try[BuildMessages] = {

    val environment = if (ApplicationManager.getApplication.isUnitTestMode && SystemInfo.isWindows) {
      val extraEnvs = defaultCoursierDirectoriesAsEnvVariables()
      environment0 ++ extraEnvs
    }
    else environment0

    Log.debugSafe(
      s"""runSbt
         |  directory: $directory,
         |  vmExecutable: $vmExecutable,
         |  vmOptions: $vmOptions,
         |  environment: $environment,
         |  sbtLauncher: $sbtLauncher,
         |  sbtCommandLineArgs: $sbtCommandLineArgs,
         |  sbtCommands: $sbtCommands,
         |  reportMessage: $reportMessage""".stripMargin
    )

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
      sbtCommandLineArgs // :+ "--debug"

    val processCommands = processCommandsRaw.filterNot(_.isEmpty)

    val dumpTaskId = EventId(s"dump:${UUID.randomUUID()}")
    reporter.startTask(dumpTaskId, None, reportMessage, startTime)

    val resultMessages = Try {
      val processBuilder = new ProcessBuilder(processCommands.asJava)
      processBuilder.directory(directory)
      processBuilder.environment().putAll(environment.asJava)
      val procString = processBuilder.command().asScala.mkString(" ")
      reporter.log(procString)

      Log.debugSafe(
        s"""processBuilder.start()
           |  command line: ${processBuilder.command().asScala.mkString(" ")}""".stripMargin
      )
      processBuilder.start()
    }
      .flatMap { process =>
        Using.resource(new PrintWriter(new BufferedWriter(new OutputStreamWriter(process.getOutputStream, "UTF-8")))) { writer =>
          writer.println(sbtCommands)
          // exit needs to be in a separate command, otherwise it will never execute when a previous command in the chain errors
          writer.println("exit")
          writer.flush()
          handle(process, dumpTaskId, reporter)
        }
      }
      .recoverWith {
        case _: ImportCancelledException =>
          Success(BuildMessages.empty.status(BuildMessages.Canceled))
        case fail =>
          Failure(ImportCancelledException(fail))
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

    val isUnitTest = ApplicationManager.getApplication.isUnitTestMode
    processOutputBuilder.clear()

    val processListener: (OutputType, String) => Unit = (typ, line) => {
      if (isUnitTest) {
        processOutputBuilder.append(s"[${typ.getClass.getSimpleName}] $line")
        if (!line.endsWith("\n")) {
          processOutputBuilder.append('\n')
        }
      }
      (typ, line) match {
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
        case _ => // ignore
      }
    }

    val handler = new OSProcessHandler(process, "sbt import", Charset.forName("UTF-8"))
    // TODO: rewrite this code, do not use try, throw
    val result = Try {
      handler.addProcessListener(new ListenerAdapter(processListener))
      Log.debug("handler.startNotify()")
      handler.startNotify()

      val start = System.currentTimeMillis()

      var processEnded = false
      while (!processEnded && !cancellationFlag.get()) {
        processEnded = handler.waitFor(SBT_PROCESS_CHECK_TIMEOUT_MSEC)

        val importIsTooLong = isUnitTest && System.currentTimeMillis() - start > MaxImportDurationInUnitTests.toMillis
        if (importIsTooLong) {
          throw new TimeoutException(s"sbt process hasn't finished in $MaxImportDurationInUnitTests")
        }
      }

      val exitCode = handler.getExitCode
      Log.debug(s"processEnded: $processEnded, exitCode: $exitCode")
      if (!processEnded)
        throw ImportCancelledException(new Exception(SbtBundle.message("sbt.task.canceled")))
      else if (exitCode != 0)
        messages.status(BuildMessages.Error)
      else if (messages.status == BuildMessages.Indeterminate)
        messages.status(BuildMessages.OK)
      else
        messages
    }
    if (!handler.isProcessTerminated) {
      Log.debug(s"sbt process has not terminated, destroying the process...")
      handler.setShouldDestroyProcessRecursively(false) // TODO: why not `true`?
      handler.destroyProcess()
    }
    result
  }
}

object SbtStructureDump {

  private val Log = Logger.getInstance(classOf[SbtStructureDump])

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

      val newMessages =
        if (text startsWith ERROR_PREFIX) {
          messages.addError(text.stripPrefix(ERROR_PREFIX))
        } else if (text startsWith WARN_PREFIX) {
          messages.addWarning(text.stripPrefix(WARN_PREFIX))
        } else messages

      reporter.progressTask(dumpTaskId, 1, -1, SbtBundle.message("sbt.events"), text)
      reporter.log(text)

      newMessages
  }

  private val WARN_PREFIX = "[warn]"
  private val ERROR_PREFIX = "[error]"

  sealed trait ImportType
  case object ShellImport extends ImportType
  case object ProcessImport extends ImportType
}
