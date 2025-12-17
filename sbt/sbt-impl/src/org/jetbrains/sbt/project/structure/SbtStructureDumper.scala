package org.jetbrains.sbt.project.structure

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.eel.provider.utils.EelPathUtils.TransferTarget
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}
import org.jetbrains.sbt.process.{ProcessOutputCollector, SbtRunner}
import org.jetbrains.sbt.project.SbtProjectResolver.ImportContext
import org.jetbrains.sbt.shell.{SbtProcessManager, SbtShellCommunication}
import org.jetbrains.sbt.{SbtBundle, SbtUtil, SbtVersion, SbtVersionCapabilities, asLocalPath, eelDescriptor}

import java.nio.file.Path
import java.util.UUID
import scala.concurrent.Future
import scala.util.Try

sealed trait SbtStructureDumper:
  protected val processOutputCollector: Option[ProcessOutputCollector] =
    ProcessOutputCollector.setUpProcessOutputCollection()

  final def processOutput: String = processOutputCollector.fold("")(_.processOutput)

  def cancel(): Unit

object SbtStructureDumper:
  final class FromShell extends SbtStructureDumper:
    // Dumping the sbt project structure from sbt-shell is not cancellable.
    override def cancel(): Unit = ()

    def dumpFromShell(
      project: Project,
      structureFile: Path,
      options: Seq[String],
      reporter: BuildReporter,
      preferScala2: Boolean,
      generateManagedSources: Boolean
    )(using context: ImportContext): Future[BuildMessages] =
      reporter.start()

      val shell = SbtShellCommunication.forProject(project)

      lazy val buildCommand: String = {
        // Re-detect the sbt version at the moment the command is built.
        // Since this is called from within `SbtShellCommunication.processCommand`,
        // #getRunningOrDetectedSbtVersion returns the version currently used in the sbt shell rather than the detected one.
        val currentSbtVersion = shell.getRunningOrDetectedSbtVersion

        context.sbtVersion = currentSbtVersion

        val optionsString = makeOptionsStringLiteral(options)
        val SeqFqn = SbtVersionCapabilities.collectionsSeqClassFqn(currentSbtVersion)
        val setCommands = Seq(
          s"""${scopedSbtSetting("_root_.org.jetbrains.sbt.StructureKeys.sbtStructureOptions", "_root_.sbt.Global", currentSbtVersion)} := $optionsString""",
          s"""${scopedSbtSetting("_root_.org.jetbrains.sbt.StructureKeys.generateManagedSourcesDuringStructureDump", "_root_.sbt.Global", currentSbtVersion)} := $generateManagedSources"""
        ).mkString(s"set $SeqFqn(", ",", ")")
        val dumpStructureToCommand = s"${SbtUtil.sbtStructureGlobalCommand("dumpStructureTo", currentSbtVersion)} ${normalizedLocalPath(structureFile)}"

        // SCL-22858 compiler bytecode indices are disabled in sbt shell
        val ideaPortSetting = ""

        val maybePreferScala2Command = if (preferScala2) "preferScala2" else ""
        buildSbtCompositeCommand(
          "reload",
          setCommands,
          maybePreferScala2Command,
          dumpStructureToCommand,
          s"session clear-all $ideaPortSetting"
        )
      }

      val optProcessOutputBuilder = processOutputCollector.map(_.processOutputBuilder)
      val aggregator = shell.messageAggregatorForSync(
        reporter,
        EventId(s"dump:${UUID.randomUUID()}"),
        optProcessOutputBuilder,
        startMessage = SbtBundle.message("sbt.extracting.project.structure.from.sbt.shell"),
        finishMessage = SbtBundle.message("sbt.project.structure.extracted")
      )

      val isSbtVersionOutdated = SbtProcessManager.forProject(project).isSbtVersionOutdated
      val terminationMessage = "Sbt shell terminated before sync command is finished"
      if isSbtVersionOutdated then
        shell.commandAfterSoftRestart(buildCommand, BuildMessages.empty, aggregator, terminationMessage)
      else
        shell.command(buildCommand, BuildMessages.empty, aggregator, Some(terminationMessage))

  end FromShell

  final class FromProcess extends SbtStructureDumper:
    private val runner: SbtRunner = SbtRunner(processOutputCollector)

    override def cancel(): Unit = runner.cancel()

    //noinspection ApiStatus,UnstableApiUsage
    def dumpFromProcess(
      indicator: ProgressIndicator,
      directory: Path,
      structureFile: Path,
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
    )(using reporter: BuildReporter): Try[BuildMessages] =
      val optString = makeOptionsStringLiteral(options)

      val sbtVersion = SbtUtil.detectSbtVersion(directory, sbtLauncher)

      val transferredStructureFile =
        EelPathUtils.transferLocalContentToRemote(structureFile, TransferTarget.Temporary(directory.eelDescriptor))

      val maybePreferScala2Command = if (preferScala2) "preferScala2" else ""

      val transferredSbtStructureJar =
        EelPathUtils.transferLocalContentToRemote(sbtStructureJar, TransferTarget.Temporary(directory.eelDescriptor))

      val dumpStructureCommand = SbtUtil.sbtStructureGlobalCommand("dumpStructure", sbtVersion)

      /*
      The new import logic is applied only to sbt 1.5.0+ due to the following issues:
      - the ability to apply plugin jars in `unmanagedJars` task in the additional sbt file passed via `addPluginSbtFile` works only since sbt 1.3.4.
        I'm not sure which exact change in sbt enabled this, as there were multiple fixes to the `addPluginSbtFile` command.
      - some sbt 1.4.x versions are broken due to missing the necessary files for the arm64 architecture,
        making it difficult to test for other potential issues.
      */
      val dumpProcessArgsMethod =
        if (sbtVersion >= SbtVersion("1.5.0")) getDumpProcessArgsForSbt_1_5_Plus
        else getDumpProcessArgsForLegacySbt

      val DumpProcessArgs(sbtCommandsString, additionalVmOptions, sbtLauncherArgs) =
        dumpProcessArgsMethod(
          transferredStructureFile,
          optString,
          generateManagedSources,
          transferredSbtStructureJar,
          maybePreferScala2Command,
          dumpStructureCommand,
          sbtVersion
        )

      val buildMessages = runner.runSbt(
        indicator,
        directory,
        vmExecutable,
        vmOptions ++ additionalVmOptions,
        environment,
        sbtLauncher,
        sbtOptions,
        sbtLauncherArgs,
        sbtCommandsString,
        SbtBundle.message("sbt.extracting.project.structure.from.sbt"),
        passParentEnvironment
      )

      copyFileContentsIfNeeded(transferredStructureFile, structureFile)

      buildMessages
    end dumpFromProcess

    private case class DumpProcessArgs(
      commandsString: String,
      additionalVmOptions: Seq[String],
      launcherArg: Seq[String]
    )

    /**
     * Build process arguments for dumping the project structure with sbt 1.5.0+.
     *
     * Differences vs legacy implementation:
     * - Pass `sbt.structure.outputFile`, `sbt.structure.options`,
     *   and `sbt.structure.generateManagedSources` system properties to the `sbt-structure` plugin.
     *   The system properties are later used in the `sbt-structure` plugin to initialize the keys.
     * - Does not set `historyPath := None` — I don't see any advantage in using this setting.
     *   Since sbt 1.4.0+, commands prepended with a space are not added to the history, and this should be enough for this kind of import.
     * - Does not set `shellPrompt := { _ => "" }` - from what I've observed, setting `shellPrompt` to `""` prevents the
     *   prompt from being displayed before shutting down the sbt process.
     *   It's a kind of cosmetic issue. Eventually it can be also set in the `sbt-structure` plugin.
     *
     * All the points above were implemented to avoid using the `set` command, which causes state transformations.
     *
     * - Loads the sbt-structure jar via the `unmanagedJars` task.
     *   This eliminates the need for the `apply -cp` that caused transformations.
     */
    private def getDumpProcessArgsForSbt_1_5_Plus(
      structureFilePath: Path,
      optString: String,
      generateManagedSources: Boolean,
      sbtStructureJar: Path,
      maybePreferScala2Command: String,
      dumpStructureCommand: String,
      sbtVersion: SbtVersion
    ): DumpProcessArgs = {
      val commands = buildSbtCompositeCommand(maybePreferScala2Command, dumpStructureCommand)
      val additionalVmOptions = Seq(
        s"-Dsbt.structure.outputFile=${normalizedLocalPath(structureFilePath)}",
        s"-Dsbt.structure.options=$optString",
        s"-Dsbt.structure.generateManagedSources=$generateManagedSources",
      )

      val fileConverter =
        if (sbtVersion.isSbt2) "given FileConverter = fileConverter.value"
        else ""
      val tmpPluginsSbtFile = SbtUtil.createTemporarySbtFile(
        raw"""Compile / unmanagedJars ++= {
             |$fileConverter
             |Seq(file("${normalizedLocalPath(sbtStructureJar)}")).classpath
             |}
             |""".stripMargin
      )
      val launcherArgs = Seq(s"-addPluginSbtFile=${tmpPluginsSbtFile.toRealPath()}")
      DumpProcessArgs(commands, additionalVmOptions, launcherArgs)
    }

    private def getDumpProcessArgsForLegacySbt(
      structureFilePath: Path,
      optString: String,
      generateManagedSources: Boolean,
      sbtStructureJar: Path,
      maybePreferScala2Command: String,
      dumpStructureCommand: String,
      sbtVersion: SbtVersion
    ): DumpProcessArgs = {
      val SeqFqn = SbtVersionCapabilities.collectionsSeqClassFqn(sbtVersion)
      val setCommands = Seq(
        """historyPath := None""",
        s"""shellPrompt := { _ => "" }""",
        s"""${scopedSbtSetting("""SettingKey[_root_.scala.Option[_root_.sbt.File]]("sbtStructureOutputFile")""", "_root_.sbt.Global", sbtVersion)} := _root_.scala.Some(_root_.sbt.file("${normalizedLocalPath(structureFilePath)}"))""",
        s"""${scopedSbtSetting("""SettingKey[_root_.java.lang.String]("sbtStructureOptions")""", "_root_.sbt.Global", sbtVersion)} := $optString""",
        s"""${scopedSbtSetting("""SettingKey[_root_.scala.Boolean]("generateManagedSourcesDuringStructureDump")""", "_root_.sbt.Global", sbtVersion)} := $generateManagedSources"""
      ).mkString(s"set $SeqFqn(", ",", ")")

      val applyStateTransformersCommand = s"""apply -cp "${normalizedLocalPath(sbtStructureJar)}" "org.jetbrains.sbt.CreateTasks" "sbt.jetbrains.LogDownloadArtifacts""""

      val commands = buildSbtCompositeCommand(
        setCommands,
        applyStateTransformersCommand,
        maybePreferScala2Command,
        dumpStructureCommand
      )
      DumpProcessArgs(commands, Nil, Nil)
    }


    private def copyFileContentsIfNeeded(remotePath: Path, localPath: Path): Unit =
      import java.io.PrintWriter
      import java.nio.charset.StandardCharsets.UTF_8
      import java.nio.file.Files
      import java.nio.file.StandardOpenOption.*
      import scala.util.Using
      if remotePath != localPath then
        Using.resource(Files.newBufferedReader(remotePath, UTF_8)): reader =>
          Using.resource(PrintWriter(Files.newBufferedWriter(localPath, UTF_8, CREATE, TRUNCATE_EXISTING, WRITE))): writer =>
            reader.lines().forEach(writer.println(_))

  end FromProcess

  private def normalizedLocalPath(path: Path): String =
    FileUtil.toSystemIndependentName(path.asLocalPath)

  private def buildSbtCompositeCommand(commands: String*): String =
    commands.filter(_.nonEmpty).mkString(";", ";", "")

  private def makeOptionsStringLiteral(options: Seq[String]): String =
    options.mkString("\"", ", ", "\"")

  private def scopedSbtSetting(setting: String, scope: String, sbtVersion: SbtVersion): String =
    val supportsSlashSyntax = SbtVersionCapabilities.isSlashSyntaxSupported(sbtVersion)
    if supportsSlashSyntax then
      s"($scope / $setting)"
    else
      s"$setting in $scope"
