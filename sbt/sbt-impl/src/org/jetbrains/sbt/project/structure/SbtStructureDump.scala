package org.jetbrains.sbt.project.structure

import com.intellij.build.events.impl.{FailureResultImpl, SkippedResultImpl, SuccessResultImpl}
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.{Nls, NonNls, Nullable}
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}
import org.jetbrains.plugins.scala.extensions.LoggerExt
import org.jetbrains.sbt.SbtUtil.normalizePath
import org.jetbrains.sbt.actions.GenerateManagedSourcesReporter
import org.jetbrains.sbt.project.SbtProjectResolver.ImportCancelledException
import org.jetbrains.sbt.project.structure.SbtOption._
import org.jetbrains.sbt.project.structure.SbtStructureDump._
import org.jetbrains.sbt.shell.{SbtProcessManager, SbtShellCommunication}
import org.jetbrains.sbt.shell.SbtShellCommunication._
import org.jetbrains.sbt.{SbtBundle, SbtUtil, SbtVersion, SbtVersionCapabilities}

import java.io.{BufferedWriter, File, OutputStreamWriter, PrintWriter}
import java.nio.charset.Charset
import java.util.UUID
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters._
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
    val aggregator = shellMessageAggregator(EventId(s"dump:${UUID.randomUUID()}"), shell, reporter, optProcessOutputBuilder)

    val isSbtVersionOutdated = SbtProcessManager.forProject(project).isSbtVersionOutdated
    if (isSbtVersionOutdated) {
      shell.commandAfterSoftRestart(sbtCommand, BuildMessages.empty, aggregator)
    } else {
      shell.command(sbtCommand, BuildMessages.empty, aggregator)
    }
  }

  def dumpFromProcess(
    directory: File,
    structureFilePath: String,
    options: Seq[String],
    vmExecutable: File,
    vmOptions: Seq[String],
    sbtOptions: Seq[String],
    environment: Map[String, String],
    sbtLauncher: File,
    sbtStructureJar: File,
    preferScala2: Boolean,
    passParentEnvironment: Boolean,
    generateManagedSources: Boolean
  )(implicit reporter: BuildReporter): Try[BuildMessages] = {
    val optString = makeOptionsStringLiteral(options)

    val sbtVersion = SbtUtil.detectSbtVersion(directory.toPath, sbtLauncher.toPath)

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
    )(indicator = null)
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

  /** Run sbt with some sbt commands. */
  def runSbt(
    directory: File,
    vmExecutable: File,
    vmOptions: Seq[String],
    environment0: Map[String, String],
    sbtLauncher: File,
    sbtOptions: Seq[String],
    sbtLauncherArgs: Seq[String],
    @NonNls sbtCommands: String,
    @Nls reportMessage: String,
    passParentEnvironment: Boolean
  )(
    @Nullable indicator: ProgressIndicator
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
        normalizePath(vmExecutable),
        "-Djline.terminal=jline.UnsupportedTerminal",
        "-Dsbt.log.noformat=true",
        "-Dfile.encoding=UTF-8"
      ) ++
        allOpts ++
        List("-jar", normalizePath(sbtLauncher)) ++
        allSbtLauncherArgs// :+ "--debug"

    val processCommands = processCommandsRaw.filterNot(_.isEmpty)

    val dumpTaskId = EventId(s"dump:${UUID.randomUUID()}")
    reporter.startTask(dumpTaskId, None, reportMessage, startTime)

    val resultMessages = Try {
      val parentEnvironmentType = if (passParentEnvironment) GeneralCommandLine.ParentEnvironmentType.CONSOLE else ParentEnvironmentType.NONE
      val generalCommandLine = new GeneralCommandLine(processCommands.asJava)
        .withParentEnvironmentType(parentEnvironmentType)
      val processBuilder = generalCommandLine.toProcessBuilder
      processBuilder.directory(directory)
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
        Using.resource(new PrintWriter(new BufferedWriter(new OutputStreamWriter(process.getOutputStream, "UTF-8")))) { writer =>

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
                     @Nullable indicator: ProgressIndicator
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

        if ((indicator ne null) && indicator.isCanceled) {
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

  private def shellMessageAggregator(
    dumpTaskId: EventId,
    shell: SbtShellCommunication,
    reporter: BuildReporter,
    processOutputBuilder: Option[StringBuilder]
  ): EventAggregator[BuildMessages] = (messages, event) => event match {
    case TaskStart =>
      reporter.startTask(dumpTaskId, None, SbtBundle.message("sbt.extracting.project.structure.from.sbt.shell"))
      messages

    case TaskComplete =>
      reporter.finishTask(dumpTaskId, SbtBundle.message("sbt.project.structure.extracted"), new SuccessResultImpl())
      val messagesUpdated =
        if (messages.status == BuildMessages.Indeterminate) messages.status(BuildMessages.OK)
        else messages
      messagesUpdated

    case ProcessTerminated =>
      //TODO: it seems like in practice "process terminated" is not used at all
      // we need to refactor the reporter API to not demand it
      reporter.finishTask(dumpTaskId, "process terminated", new SuccessResultImpl())
      messages
        .addError("process terminated")
        .status(BuildMessages.Canceled)

    case ErrorWaitForInput =>
      val msg = SbtBundle.message("sbt.import.errors.project.reload.aborted")
      val ex = new ExternalSystemException(msg)

      val result = new FailureResultImpl(msg, ex)
      reporter.finishTask(dumpTaskId, msg, result)

      shell.send("i" + System.lineSeparator)

      messages.addError(msg)

    case Output(raw) =>
      val text = raw.trim
      processOutputBuilder.foreach(_.append(text))

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

  private def scopedSbtSetting(setting: String, scope: String, sbtVersion: SbtVersion): String = {
    val supportsSlashSyntax = SbtVersionCapabilities.isSlashSyntaxSupported(sbtVersion)
    if (supportsSlashSyntax)
      s"($scope / $setting)"
    else
      s"$setting in $scope"
  }
}
