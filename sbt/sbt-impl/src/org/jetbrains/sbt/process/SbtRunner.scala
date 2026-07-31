package org.jetbrains.sbt.process

import com.intellij.build.events.impl.{FailureResultImpl, SkippedResultImpl, SuccessResultImpl}
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.eel.provider.utils.EelPathUtils.TransferTarget
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.{Nls, NonNls}
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}
import org.jetbrains.plugins.scala.extensions.{LoggerExt, PathExt}
import org.jetbrains.sbt.actions.GenerateManagedSourcesReporter
import org.jetbrains.sbt.process.mock.MockSbtProcessForTests
import org.jetbrains.sbt.process.options.{SbtProcessOptions, SbtProcessOptionsResolver}
import org.jetbrains.sbt.project.SbtProjectResolver.ImportCancelledException
import org.jetbrains.sbt.project.settings.SbtExecutionSettings
import org.jetbrains.sbt.project.structure.{ListenerAdapter, OutputType}
import org.jetbrains.sbt.{SbtBundle, asLocalPath, eelDescriptor}

import java.io.{BufferedWriter, OutputStreamWriter, PrintWriter}
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.TimeoutException
import scala.concurrent.duration.{FiniteDuration, given}
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try, Using}

final class SbtRunner(processOutputCollector: Option[SbtProcessOutputDiagnosticsCollector] = None):
  import SbtRunner.*

  private val cancellationFlag: AtomicBoolean = new AtomicBoolean(false)

  def cancel(): Unit = cancellationFlag.set(true)

  @RequiresBackgroundThread
  def runSbt(
    indicator: ProgressIndicator,
    directory: Path,
    vmExecutable: Path,
    vmOptions: Seq[String],
    environment0: Map[String, String],
    sbtLauncher: Path,
    sbtOptions: SbtExecutionSettings.SbtOptions,
    sbtLauncherArgs: Seq[String],
    @NonNls sbtCommands: String,
    @Nls reportMessage: String,
    passParentEnvironment: Boolean,
    timingCollector: Option[SbtImportTimingCollector.TimingCollector],
    project: Option[Project] = None,
  )(
    implicit reporter: BuildReporter
  ): Try[BuildMessages] =
    runSbt(
      indicator,
      directory,
      vmExecutable,
      environment0,
      sbtLauncher,
      sbtCommands,
      reportMessage,
      passParentEnvironment,
      timingCollector,
      sbtProcessOptions = SbtProcessOptionsResolver.resolveForSeparateProcess(
        directory,
        vmOptions,
        sbtOptions.options,
        EnvironmentVariablesData.create(environment0.asJava, passParentEnvironment),
        sbtLauncherArgs,
        sbtOptions.malformedOptions
      ),
      project = project,
    )

  /**
   * Runs sbt via the `java -jar sbt-launch.jar` mechanism.
   * The working directory, the java vm executable, and the sbt-launch.jar need to be paths on the local machine where
   * the process will be executed. In most cases, this means simply the same machine where IntelliJ IDEA is running,
   * with the vm executable pointing to a local JDK installation, the working directory pointing to the current project
   * directory, and the sbt-launch.jar path pointing to the sbt-launch.jar distributed within the Scala Plugin. In the
   * case of running using WSL, the IDE will be installed on the regular Windows installation, while the JDK, the
   * project directory and the sbt-launcher will point to paths inside the virtualized Linux WSL environment. In both
   * cases, the eel API will be used to translate local filesystem paths to the paths on the remote machines as well
   * as running the actual OS process. In the case of everything running on a local machine, this work will essentially
   * be a no-op, with the local and remote paths matching, as well as the OS process running mechanism being the same
   * as the local one.
   *
   * @note This method must run on a background thread behind a progress indicator.
   * @note If any of the options or commands arguments also refer to filesystem paths, it is up to the caller to
   *       translate these paths to the required target machine. The options and commands are passed as provided and
   *       not interpreted in any way.
   * @param indicator             The required progress indicator instance
   * @param directory             The working directory of the JVM process to be spawned
   * @param vmExecutable          The path to the JDK `java` executable
   * @param environment0          Environment variables to be provided to the `java` process
   * @param sbtLauncher           A path to the sbt launcher jar
   * @param sbtCommands           A list of sbt commands to be executed by the spawned sbt process
   * @param reportMessage         A description message to be provided to the reporting mechanism (usually shown to the end user)
   * @param passParentEnvironment Include the environment variables available to IntelliJ IDEA when starting the process
   * @param reporter              A build reported instance for flexibly reporting different aspects of the status of the sbt process
   * @return A set of messages (success or failure) reported by the execution of the sbt process.
   */
  @RequiresBackgroundThread
  def runSbt(
    indicator: ProgressIndicator,
    directory: Path,
    vmExecutable: Path,
    environment0: Map[String, String],
    sbtLauncher: Path,
    @NonNls sbtCommands: String,
    @Nls reportMessage: String,
    passParentEnvironment: Boolean,
    timingCollector: Option[SbtImportTimingCollector.TimingCollector],
    sbtProcessOptions: SbtProcessOptions,
    project: Option[Project],
  )(
    implicit reporter: BuildReporter
  ): Try[BuildMessages] = {

    val environment = if (isUnitTestMode && SystemInfo.isWindows) {
      val extraEnvs = defaultCoursierDirectoriesAsEnvVariables()
      environment0 ++ extraEnvs
    }
    else environment0

    Log.debugSafe(
      s"""runSbt
         |  directory: $directory,
         |  vmExecutable: $vmExecutable,
         |  allVmOptions: ${sbtProcessOptions.allVmOptions},
         |  environment: $environment,
         |  sbtLauncher: $sbtLauncher,
         |  sbtLauncherArguments: ${sbtProcessOptions.sbtLauncherArgs},
         |  sbtCommands: $sbtCommands,
         |  reportMessage: $reportMessage""".stripMargin
    )

    val startTime = System.currentTimeMillis()

    val dumpTaskId = EventId(s"dump:${UUID.randomUUID()}")
    reporter.startTask(dumpTaskId, None, reportMessage, startTime)

    val resultMessages = Try {
      val useMockSbt = project.exists(MockSbtProcessForTests.isEnabled)
      val sbtLaunchCommand: Seq[String] =
        if (useMockSbt) {
          val mockProcessCommandLineTail = project.toSeq.flatMap(MockSbtProcessForTests.mockMainClassCommandLineTailForNonShellFromStdin)
          mockProcessCommandLineTail ++ sbtProcessOptions.sbtLauncherArgs
        } else {
          //noinspection ApiStatus,UnstableApiUsage
          val transferredSbtLauncher = EelPathUtils.transferLocalContentToRemote(sbtLauncher, TransferTarget.Temporary(directory.eelDescriptor))
          validateAllPathsHaveTheSameEelDescriptor(directory, vmExecutable, transferredSbtLauncher)
          List("-jar", transferredSbtLauncher.asLocalPath) ++ sbtProcessOptions.sbtLauncherArgs
        }

      val processCommandsRaw =
        List(
          vmExecutable.toString,
          "-Djline.terminal=jline.UnsupportedTerminal",
          "-Dsbt.log.noformat=true",
          "-Dfile.encoding=UTF-8"
        ) ++
          sbtProcessOptions.allVmOptions ++
          sbtLaunchCommand // :+ "--debug"

      val processCommands = processCommandsRaw.filterNot(_.isEmpty)

      val parentEnvironmentType = if (passParentEnvironment) GeneralCommandLine.ParentEnvironmentType.CONSOLE else ParentEnvironmentType.NONE
      // It is required due to #SCL-19498
      val fullEnvironment = environment + ("HISTCONTROL" -> "ignorespace")
      val commandLine =
        new GeneralCommandLine(processCommands.asJava)
          .withParentEnvironmentType(parentEnvironmentType)
          .withWorkingDirectory(directory)
          .withEnvironment(fullEnvironment.asJava)
      val procString = commandLine.getCommandLineString
      reporter.log(procString)

      Log.debugSafe(
        s"""processBuilder.start()
           |  command line: $procString""".stripMargin
      )
      commandLine.createProcess()
    }
      .flatMap { process =>
        Using.resource(new PrintWriter(new BufferedWriter(new OutputStreamWriter(process.getOutputStream, StandardCharsets.UTF_8)))) { writer =>

          writer.println(ignoreInShellHistory(sbtCommands))
          // exit needs to be in a separate command, otherwise it will never execute when a previous command in the chain errors
          writer.println(ignoreInShellHistory("exit"))
          writer.flush()
          handle(process, dumpTaskId, reporter, indicator, timingCollector)
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
            new FailureResultImpl()
        }

      case Failure(x) =>
        new FailureResultImpl(x)
    }

    reporter.finishTask(dumpTaskId, reportMessage, eventResult)

    resultMessages
  }

  private def handle(process: Process,
                     dumpTaskId: EventId,
                     reporter: BuildReporter,
                     indicator: ProgressIndicator,
                     timingCollector: Option[SbtImportTimingCollector.TimingCollector]
                    ): Try[BuildMessages] = {

    var messages = BuildMessages.empty
    val outputDumpRecorder = new SbtImportOutputDumpRecorder(
      enabledInCurrentRun = isUnitTestMode
    )
    val heartbeatReporter = new SbtImportHeartbeatReporter(
      heartbeatInterval = ImportHeartbeatIntervalInUnitTests,
      enabledInCurrentRun = isUnitTestMode,
      outputDumpRecorder = outputDumpRecorder
    )

    def update(typ: OutputType, textRaw: String): Unit = {
      timingCollector.foreach(_.processSbtOutputLine(textRaw))

      reporter match {
        case _: GenerateManagedSourcesReporter =>
          // The SbtGenerateManagedSourcesAction is sensitive to the exact output of the sbt process.
          // In large projects, where many managed sources are generated at once, it can happen that the process
          // management API splits the sbt output from one line across several lines. This can be detected by
          // looking at the raw strings reported. The split lines do not have a '\n' newline character at the end.
          messages = reportEvent(messages, textRaw)
          reporter.log(textRaw)

        case _ =>
          val text = textRaw.trim

          if (text.nonEmpty) {
            messages = reportEvent(messages, text)
            reporter.progressTask(dumpTaskId, 1, -1, "", text)
            typ match {
              case OutputType.StdErr =>
                reporter.logErr(text)
              case _ =>
                reporter.log(text)
            }
          }
      }
    }

    val processListener: (OutputType, String) => Unit = (typ, line) => {
      (typ, line) match {
        case (typ@OutputType.StdOut, text) =>
          outputDumpRecorder.onProcessOutput(typ, text)
          if (text.contains("(q)uit")) {
            val writer = new PrintWriter(process.getOutputStream)
            writer.println("q")
            writer.close()
          } else {
            update(typ, text)
          }
        case (typ@OutputType.StdErr, text) =>
          outputDumpRecorder.onProcessOutput(typ, text)
          update(typ, text)
        case _ => // ignore
      }
    }

    val handler = new OSProcessHandler(process, "sbt import", StandardCharsets.UTF_8)
    // TODO: rewrite this code, do not use try, throw
    val result = Try {
      SbtProcessOutputDiagnosticsCollector.collectProcessOutputFrom(handler, processTitle = "SBT separate process output")
      processOutputCollector.foreach(_.collectProcessOutputFrom(handler, processTitle = "SBT separate process output"))
      handler.addProcessListener(new ListenerAdapter(processListener))
      Log.debug("handler.startNotify()")
      handler.startNotify()

      val start = System.currentTimeMillis()
      heartbeatReporter.initialize(start)

      var processEnded = false
      while (!processEnded && !cancellationFlag.get()) {
        processEnded = handler.waitFor(SBT_PROCESS_CHECK_TIMEOUT_MS)

        val now = System.currentTimeMillis()
        heartbeatReporter.reportIfDue(now, start)

        if (indicator.isCanceled) {
          cancellationFlag.set(true)
        }

        val importIsTooLong = isUnitTestMode && ((now - start) > MaxImportDurationInUnitTests.toMillis)
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

  private def reportEvent(messages: BuildMessages,
                          text: String): BuildMessages = {

    if (isUnitTestMode && !dontPrintErrorsAndWarningsToConsoleDuringTests) {
      val isErrorOrWarning = text.startsWith("[warn]") || text.startsWith("[error]")
      if (isErrorOrWarning) {
        System.err.println(text)
      }
    }
    //NOTE: we can't simply check for "[error]" prefix, some output errors might still not fail the build
    //See to SCL-21478 and SCL-13038
    val setBuildStatusToError = text.startsWith("[error] Total time")
    if (setBuildStatusToError && messages.status != BuildMessages.Error) {
      messages
        .status(BuildMessages.Error)
        .addError(SbtBundle.message("sbt.import.check.root.node.for.details"))
    } else messages
  }

  private def dontPrintErrorsAndWarningsToConsoleDuringTests: Boolean =
    System.getProperty("sbt.structure.dump.dontPrintErrorsAndWarningsToConsoleDuringTests") == "true"

  // Due to #SCL-19498 it is needed to prepend each command with empty space at the beginning
  private def ignoreInShellHistory(command: String): String = command.prependedAll(" ")

  private def isUnitTestMode: Boolean = ApplicationManager.getApplication.isUnitTestMode

  private def validateAllPathsHaveTheSameEelDescriptor(paths: Path*): Unit =
    if paths.isEmpty then return
    val descriptor = paths.head.eelDescriptor
    val allEqual = paths.forall(_.eelDescriptor == descriptor)
    if !allEqual then
      throw IllegalStateException(
        s"""The paths:
           |${paths.mkString(", ")}
           |are not compatible. They point to paths in different (virtual) machines.
           |Please check your project configuration, sbt settings and project JDK settings.
           |""".stripMargin
      )

end SbtRunner

object SbtRunner:
  private val Log: Logger = Logger.getInstance(classOf[SbtRunner])

  private val SBT_PROCESS_CHECK_TIMEOUT_MS = 100

  // NOTE: if this is a first run of sbt with a particular version on current machine,
  // sbt import will take some time because it will have to download quite a lot of dependencies
  private[sbt] val MaxImportDurationInUnitTests: FiniteDuration = 10.minutes
  private[sbt] val ImportHeartbeatIntervalInUnitTests: FiniteDuration = 30.seconds

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
  private[jetbrains] def defaultCoursierDirectoriesAsEnvVariables(): Map[String, String] =
    val LocalAppData = System.getenv("LOCALAPPDATA")
    val AppData = System.getenv("APPDATA")

    val CoursierLocalAppDataHome = Path.of(LocalAppData, "Coursier")
    val CoursierAppDataHome = Path.of(AppData, "Coursier")

    Map(
      ("COURSIER_CACHE", CoursierLocalAppDataHome / "cache" / "v1"),
      ("COURSIER_ARCHIVE_CACHE", CoursierLocalAppDataHome / "cache" / "arc"),
      ("COURSIER_JVM_CACHE", CoursierLocalAppDataHome / "cache" / "jvm"),
      ("COURSIER_CONFIG_DIR", CoursierAppDataHome / "config"),
      ("COURSIER_DATA_DIR", CoursierLocalAppDataHome / "data"),
      ("SCALA_CLI_CONFIG", CoursierAppDataHome / "config" / "secrets" / "config.json")
    ).map((env, path) => (env, path.toCanonicalPath.toString))
  end defaultCoursierDirectoriesAsEnvVariables
