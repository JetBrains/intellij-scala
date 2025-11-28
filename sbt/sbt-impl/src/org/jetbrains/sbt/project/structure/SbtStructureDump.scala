package org.jetbrains.sbt.project.structure

import com.intellij.build.events.impl.{FailureResultImpl, SkippedResultImpl, SuccessResultImpl}
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.{Nls, NonNls}
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}
import org.jetbrains.plugins.scala.extensions.LoggerExt
import org.jetbrains.sbt.actions.GenerateManagedSourcesReporter
import org.jetbrains.sbt.project.SbtProjectResolver.ImportCancelledException
import org.jetbrains.sbt.project.structure.SbtOption.*
import org.jetbrains.sbt.project.structure.SbtStructureDump.*
import org.jetbrains.sbt.shell.{SbtProcessManager, SbtShellCommunication}
import org.jetbrains.sbt.{SbtBundle, SbtUtil, SbtVersion, SbtVersionCapabilities}

import java.io.{BufferedWriter, OutputStreamWriter, PrintWriter}
import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try, Using}

class SbtStructureDump {

  private val cancellationFlag: AtomicBoolean = new AtomicBoolean(false)

  // in failed tests we would like to see sbt process output
  private val processOutputBuilder = new mutable.StringBuilder
  def processOutput: String = processOutputBuilder.mkString

  def cancel(): Unit = cancellationFlag.set(true)

  def dumpFromShell(
    project: Project,
    sbtVersion: SbtVersion,
    structureFilePath: String,
    options: Seq[String],
    reporter: BuildReporter,
    preferScala2: Boolean,
    generateManagedSources: Boolean
  ): Future[BuildMessages] = {
    reporter.start()

    val optionsString = makeOptionsStringLiteral(options)
    val SeqFqn = SbtVersionCapabilities.collectionsSeqClassFqn(sbtVersion)
    val setCommands = Seq(
      s"""${scopedSbtSetting("_root_.org.jetbrains.sbt.StructureKeys.sbtStructureOptions", "_root_.sbt.Global", sbtVersion)} := $optionsString""",
      s"""${scopedSbtSetting("_root_.org.jetbrains.sbt.StructureKeys.generateManagedSourcesDuringStructureDump", "_root_.sbt.Global", sbtVersion)} := $generateManagedSources"""
    ).mkString(s"set $SeqFqn(", ",", ")")
    val dumpStructureToCommand = s"${SbtUtil.sbtStructureGlobalCommand("dumpStructureTo", sbtVersion)} $structureFilePath"

    // SCL-22858 compiler bytecode indices are disabled in sbt shell
    val ideaPortSetting = ""

    val maybePreferScala2Command = if (preferScala2) "preferScala2" else ""
    val sbtCommand = buildSbtCompositeCommand(Seq(
      "reload",
      setCommands,
      maybePreferScala2Command,
      dumpStructureToCommand,
      s"session clear-all $ideaPortSetting"
    ))

    val shell = SbtShellCommunication.forProject(project)
    val optProcessOutputBuilder = setUpProcessOutputCollection()
    val aggregator = shell.messageAggregatorForSync(
      reporter,
      EventId(s"dump:${UUID.randomUUID()}"),
      optProcessOutputBuilder,
      startMessage = SbtBundle.message("sbt.extracting.project.structure.from.sbt.shell"),
      finishMessage = SbtBundle.message("sbt.project.structure.extracted")
    )

    val isSbtVersionOutdated = SbtProcessManager.forProject(project).isSbtVersionOutdated
    val terminationMessage = "Sbt shell terminated before sync command is finished"
    if (isSbtVersionOutdated) {
      shell.commandAfterSoftRestart(sbtCommand, BuildMessages.empty, aggregator, terminationMessage)
    } else {
      shell.command(sbtCommand, BuildMessages.empty, aggregator, Some(terminationMessage))
    }
  }

  def dumpFromProcess(
    indicator: ProgressIndicator,
    directory: Path,
    structureFilePath: String,
    options: Seq[String],
    vmExecutable: Path,
    vmOptions: Seq[String],
    sbtOptions: Seq[String],
    environment: Map[String, String],
    sbtLauncher: Path,
    sbtStructureJar: Path,
    preferScala2: Boolean,
    passParentEnvironment: Boolean,
    generateManagedSources: Boolean
  )(implicit reporter: BuildReporter): Try[BuildMessages] = {
    val optString = makeOptionsStringLiteral(options)

    val sbtVersion = SbtUtil.detectSbtVersion(directory, sbtLauncher)

    val SeqFqn = SbtVersionCapabilities.collectionsSeqClassFqn(sbtVersion)
    val setCommands = Seq(
      """historyPath := None""",
      s"""shellPrompt := { _ => "" }""",
      s"""${scopedSbtSetting("""SettingKey[_root_.scala.Option[_root_.sbt.File]]("sbtStructureOutputFile")""", "_root_.sbt.Global", sbtVersion)} := _root_.scala.Some(_root_.sbt.file("$structureFilePath"))""",
      s"""${scopedSbtSetting("""SettingKey[_root_.java.lang.String]("sbtStructureOptions")""", "_root_.sbt.Global", sbtVersion)} := $optString""",
      s"""${scopedSbtSetting("""SettingKey[_root_.scala.Boolean]("generateManagedSourcesDuringStructureDump")""", "_root_.sbt.Global", sbtVersion)} := $generateManagedSources"""
    ).mkString(s"set $SeqFqn(", ",", ")")

    val maybePreferScala2Command = if (preferScala2) "preferScala2" else ""
    val applyStateTransformersCommand = s"""apply -cp "${SbtUtil.normalizePath(sbtStructureJar)}" "org.jetbrains.sbt.CreateTasks" "sbt.jetbrains.LogDownloadArtifacts""""

    val sbtCommandsString = buildSbtCompositeCommand(Seq(
      setCommands,
      applyStateTransformersCommand,
      maybePreferScala2Command,
      SbtUtil.sbtStructureGlobalCommand("dumpStructure", sbtVersion)
    ))

    runSbt(
      indicator,
      directory,
      vmExecutable,
      vmOptions,
      environment,
      sbtLauncher,
      sbtOptions,
      sbtLauncherArgs = Seq.empty,
      sbtCommandsString,
      SbtBundle.message("sbt.extracting.project.structure.from.sbt"),
      passParentEnvironment
    )
  }

  private def buildSbtCompositeCommand(commands: Seq[String]): String =
    commands.filter(_.nonEmpty).mkString(";", ";", "")

  private def makeOptionsStringLiteral(options: Seq[String]): String =
    options.mkString("\"", ", ", "\"")

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
   *
   * @param indicator The required progress indicator instance
   * @param directory The working directory of the JVM process to be spawned
   * @param vmExecutable The path to the JDK `java` executable
   * @param vmOptions JDK VM options passed directly to the `java` process
   * @param environment0 Environment variables to be provided to the `java` process
   * @param sbtLauncher A path to the sbt launcher jar
   * @param sbtOptions A list of options to be provided to sbt
   * @param sbtLauncherArgs A list of extra launcher arguments to be provided during sbt startup
   * @param sbtCommands A list of sbt commands to be executed by the spawned sbt process
   * @param reportMessage A description message to be provided to the reporting mechanism (usually shown to the end user)
   * @param passParentEnvironment Include the environment variables available to IntelliJ IDEA when starting the process
   * @param reporter A build reported instance for flexibly reporting different aspects of the status of the sbt process
   * @return A set of messages (success or failure) reported by the execution of the sbt process.
   */
  @RequiresBackgroundThread
  def runSbt(
    indicator: ProgressIndicator,
    directory: Path,
    vmExecutable: Path,
    vmOptions: Seq[String],
    environment0: Map[String, String],
    sbtLauncher: Path,
    sbtOptions: Seq[String],
    sbtLauncherArgs: Seq[String],
    @NonNls sbtCommands: String,
    @Nls reportMessage: String,
    passParentEnvironment: Boolean
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
         |  vmOptions: $vmOptions,
         |  environment: $environment,
         |  sbtLauncher: $sbtLauncher,
         |  sbtOptions: $sbtOptions,
         |  sbtLauncherArguments: $sbtLauncherArgs,
         |  sbtCommands: $sbtCommands,
         |  reportMessage: $reportMessage""".stripMargin
    )

    val startTime = System.currentTimeMillis()
    // assuming here that this method might still be called without valid project

    val sbtOpts = SbtUtil.collectAllOptionsFromSbt(sbtOptions, directory, passParentEnvironment, environment0)
    val allOpts = SbtUtil.collectAllOptionsFromJava(directory, vmOptions, passParentEnvironment, environment0) ++ sbtOpts.collect { case a: JvmOptionGlobal => a.value }

    val allSbtLauncherArgs = sbtOpts.collect { case a: SbtLauncherOption => a.value } ++ sbtLauncherArgs
    val processCommandsRaw =
      List(
        SbtUtil.normalizePath(vmExecutable),
        "-Djline.terminal=jline.UnsupportedTerminal",
        "-Dsbt.log.noformat=true",
        "-Dfile.encoding=UTF-8"
      ) ++
        allOpts ++
        List("-jar", SbtUtil.normalizePath(sbtLauncher)) ++
        allSbtLauncherArgs// :+ "--debug"

    val processCommands = processCommandsRaw.filterNot(_.isEmpty)

    val dumpTaskId = EventId(s"dump:${UUID.randomUUID()}")
    reporter.startTask(dumpTaskId, None, reportMessage, startTime)

    val resultMessages = Try {
      // Will be replaced with eel API soon.
      val parentEnvironmentType = if (passParentEnvironment) GeneralCommandLine.ParentEnvironmentType.CONSOLE else ParentEnvironmentType.NONE
      val generalCommandLine = new GeneralCommandLine(processCommands.asJava)
        .withParentEnvironmentType(parentEnvironmentType)
      val processBuilder = generalCommandLine.toProcessBuilder
      processBuilder.directory(directory.toFile)
      processBuilder.environment().putAll(environment.asJava)
      // It is required due to #SCL-19498
      processBuilder.environment().put("HISTCONTROL", "ignorespace")
      val procString = processBuilder.command().asScala.mkString(" ")
      reporter.log(procString)

      Log.debugSafe(
        s"""processBuilder.start()
           |  command line: ${processBuilder.command().asScala.mkString(" ")}""".stripMargin
      )
      processBuilder.start()
    }
      .flatMap { process =>
        Using.resource(new PrintWriter(new BufferedWriter(new OutputStreamWriter(process.getOutputStream, StandardCharsets.UTF_8)))) { writer =>

          writer.println(ignoreInShellHistory(sbtCommands))
          // exit needs to be in a separate command, otherwise it will never execute when a previous command in the chain errors
          writer.println(ignoreInShellHistory("exit"))
          writer.flush()
          handle(process, dumpTaskId, reporter, indicator)
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

  // Due to #SCL-19498 it is needed to prepend each command with empty space at the beginning
  private def ignoreInShellHistory(command: String): String = command.prependedAll(" ")

  private def handle(process: Process,
                     dumpTaskId: EventId,
                     reporter: BuildReporter,
                     indicator: ProgressIndicator
                    ): Try[BuildMessages] = {

    var messages = BuildMessages.empty

    def update(typ: OutputType, textRaw: String): Unit = {
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

    val optProcessOutputBuilder = setUpProcessOutputCollection()

    val processListener: (OutputType, String) => Unit = (typ, line) => {
      optProcessOutputBuilder.foreach { builder =>
        builder.append(s"[${typ.name}] $line")
        if (!line.endsWith("\n")) {
          builder.append('\n')
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
        processEnded = handler.waitFor(SBT_PROCESS_CHECK_TIMEOUT_MS)

        if (indicator.isCanceled) {
          cancellationFlag.set(true)
        }

        val importIsTooLong = isUnitTestMode && System.currentTimeMillis() - start > MaxImportDurationInUnitTests.toMillis
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

  /**
   * Sets up a [[StringBuilder]] for collecting the raw process output such that it can be examined in tests.
   * @return [[Some]] if the process output should be collected, [[None]] otherwise.
   */
  private def setUpProcessOutputCollection(): Option[StringBuilder] = {
    val collectProcessOutput = isUnitTestMode || java.lang.Boolean.getBoolean(PrintProcessOutputOnFailurePropertyName)
    Log.debug(s"collectProcessOutput = $collectProcessOutput")
    processOutputBuilder.clear()
    if (collectProcessOutput) Some(processOutputBuilder) else None
  }
}

object SbtStructureDump {

  private val Log = Logger.getInstance(classOf[SbtStructureDump])

  private val SBT_PROCESS_CHECK_TIMEOUT_MS = 100

  // NOTE: if this is a first run of sbt with a particular version on current machine
  // sbt import will take some time because it will have to download quite a lot of dependencies
  private[project] val MaxImportDurationInUnitTests: FiniteDuration = 10.minutes

  val PrintProcessOutputOnFailurePropertyName = "sbt.import.print.process.output.on.failure"

  private def dontPrintErrorsAndWarningsToConsoleDuringTests: Boolean =
    System.getProperty("sbt.structure.dump.dontPrintErrorsAndWarningsToConsoleDuringTests") == "true"

  private def isUnitTestMode: Boolean = ApplicationManager.getApplication.isUnitTestMode

  private def reportEvent(messages: BuildMessages,
                          text: String): BuildMessages = {

    if (isUnitTestMode && !dontPrintErrorsAndWarningsToConsoleDuringTests) {
      val isErrorOrWarning = text.startsWith("[warn]") || text.startsWith("[error]")
      if (isErrorOrWarning){
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

  private def scopedSbtSetting(setting: String, scope: String, sbtVersion: SbtVersion): String = {
    val supportsSlashSyntax = SbtVersionCapabilities.isSlashSyntaxSupported(sbtVersion)
    if (supportsSlashSyntax)
      s"($scope / $setting)"
    else
      s"$setting in $scope"
  }
}
