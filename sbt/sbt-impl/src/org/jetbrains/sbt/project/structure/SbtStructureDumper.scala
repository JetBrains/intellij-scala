package org.jetbrains.sbt.project.structure

import com.intellij.execution.configurations.ParametersList
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.eel.provider.utils.EelPathUtils.TransferTarget
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.sbt.SbtUtil.SbtProcessOptions
import org.jetbrains.sbt.process.{ProcessOutputCollector, SbtRunner}
import org.jetbrains.sbt.project.SbtProjectResolver.ImportContext
import org.jetbrains.sbt.shell.{SbtProcessManager, SbtShellCommunication}
import org.jetbrains.sbt.{Sbt, SbtBundle, SbtUtil, SbtVersion, SbtVersionCapabilities, asLocalPath, eelDescriptor}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
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
        val dumpStructureToCommand = s"${SbtUtil.sbtStructureGlobalCommand("dumpStructureTo", currentSbtVersion)} ${normalizedLocalPath(structureFile)}"

        // SCL-22858 compiler bytecode indices are disabled in sbt shell
        val ideaPortSetting = ""

        val maybePreferScala2Command = if (preferScala2) "preferScala2" else ""

        // The new import method using the `eval` command does not work well for sbt 0.13 and 1.1.x versions.
        // I noticed it started working since sbt 1.3.x, so let's enable it from this version.
        val isMinimumSbt = currentSbtVersion >= SbtVersion("1.3.0")

        // The registry is added as a safety fallback. If nothing goes wrong over 1-2 releases, it could be removed.
        if (Registry.is("sbt.shell.import.old") || !isMinimumSbt) {
          val SeqFqn = SbtVersionCapabilities.collectionsSeqClassFqn(currentSbtVersion)
          val setCommands = Seq(
            s"""${scopedSbtSetting("_root_.org.jetbrains.sbt.StructureKeys.sbtStructureOptions", "_root_.sbt.Global", currentSbtVersion)} := $optionsString""",
            s"""${scopedSbtSetting("_root_.org.jetbrains.sbt.StructureKeys.generateManagedSourcesDuringStructureDump", "_root_.sbt.Global", currentSbtVersion)} := $generateManagedSources"""
          ).mkString(s"set $SeqFqn(", ",", ")")

          buildSbtCompositeCommand(
            "reload",
            setCommands,
            maybePreferScala2Command,
            dumpStructureToCommand,
            s"session clear-all $ideaPortSetting"
          )
        } else {
          // The system properties will later be used in the `sbt-structure` plugin to initialize settings.
          // The same trick is used in #dumpFromProcess.
          // Setting the properties with `System.setProperty(..)` affects the sbt settings initialization
          // because the `reload` command is executed afterward.
          // At the end of the `eval` command, () is added. This is done because the `eval` command always
          // prints its return value to the terminal. Without this, it would sometimes print `true` or `false`,
          // depending on what was returned from the last `System.setProperty(..)`.
          // To make the output more consistent, I added () so it always returns the same value ([info] ans: Unit = null).
          val setSystemProps =
            s"""eval {
               |System.setProperty("sbt.structure.options", $optionsString);
               |System.setProperty("sbt.structure.generateManagedSources", "${generateManagedSources.toString}");
               |() }""".stripMargin.replace('\n', ' ')

          // `session clear-all` is not used with this import method because, effectively, nothing needs to be cleared.
          // When it is used, the message "No session settings defined" is displayed in the shell.
          // If, in the future, any session settings are modified, maybe this should be added again.
          buildSbtCompositeCommand(
            setSystemProps,
            "reload",
            maybePreferScala2Command,
            dumpStructureToCommand
          )
        }
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

    private val log = Logger.getInstance(getClass)

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
    )(using reporter: BuildReporter, context: ImportContext): Try[BuildMessages] =
      val optString = makeOptionsStringLiteral(options)

      val sbtVersion = SbtUtil.detectSbtVersion(directory, sbtLauncher)

      val targetEelDescriptor = directory.eelDescriptor

      val transferredStructureFile =
        EelPathUtils.transferLocalContentToRemote(structureFile, TransferTarget.Temporary(targetEelDescriptor))

      val maybePreferScala2Command = if (preferScala2) "preferScala2" else ""

      val transferredSbtStructureJar =
        EelPathUtils.transferLocalContentToRemote(sbtStructureJar, TransferTarget.Temporary(targetEelDescriptor))

      val dumpStructureCommand = SbtUtil.sbtStructureGlobalCommand("dumpStructure", sbtVersion)

      val sbtTaskTimingOption =
        if (context.timingCollector.nonEmpty) Seq("-Dsbt.task.timings=true")
        else Nil

      /*
       The new import logic:
       - Passes additional system properties to the `sbt-structure` plugin (e.g., `sbt.structure.outputFile`), which are later used to initialize some settings.
         This avoids state transformations by replacing `set` commands.
       - Does not set `historyPath := None` — I don't see any advantage in using this setting.
         Since sbt 1.4.0+, commands prepended with a space are not added to the history, which should be sufficient for this kind of import.
       - Does not set `shellPrompt := { _ => "" }` — from what I've observed, setting `shellPrompt` to `""` prevents the
         prompt from being displayed before shutting down the sbt process (only a cosmetic issue).
       - Currently loads the `sbt-structure` plugin via the file in the global plugin directory instead of `--addPluginSbtFile` (due to https://github.com/sbt/sbt/issues/8570).
         Will be migrated later (see `getDumpProcessArgsForNewImport`).
       
       Applied only to sbt 1.5.0+ because:
       - the ability to apply plugin jars via `unmanagedJars` has worked only since sbt 1.3.4.
         I'm not sure which exact change in sbt enabled this, as there were multiple fixes to the `addPluginSbtFile` command.
       - some sbt 1.4.x versions are broken due to missing the necessary files for the arm64 architecture,
         making it difficult to test for other potential issues.
       Does not apply to sbt 2.x due to https://github.com/sbt/sbt/issues/8600.
       However, this is not critical because the next sbt 2.x version will include the fix for https://github.com/sbt/sbt/issues/8570,
       allowing us to safely use the `--addPluginSbtFile` approach.
      */
      val isNewImportEnabled = sbtVersion >= SbtVersion("1.5.0") && !sbtVersion.isSbt2

      val additionalVmOptionsForNewImport =
        if (isNewImportEnabled)
          Seq(
            s"-Dsbt.structure.outputFile=${normalizedLocalPath(transferredStructureFile)}",
            s"-Dsbt.structure.options=$optString",
            s"-Dsbt.structure.generateManagedSources=$generateManagedSources",
          )
        else Nil

      val sbtProcessOptions = SbtUtil.collectAllOptions(
        directory,
        vmOptions ++ additionalVmOptionsForNewImport ++ sbtTaskTimingOption,
        sbtOptions,
        passParentEnvironment,
        environment,
        additionalLauncherArgs = Nil
      )

      val dumpProcessArgsMethod =
        if (isNewImportEnabled) getDumpProcessArgsForNewImport
        else getDumpProcessArgsForLegacySbt

      val StructureDumpConfig(sbtCommandsString, extraSbtFileToRemove) =
        dumpProcessArgsMethod(
          directory,
          transferredStructureFile,
          targetEelDescriptor,
          optString,
          generateManagedSources,
          transferredSbtStructureJar,
          maybePreferScala2Command,
          dumpStructureCommand,
          sbtVersion,
          sbtProcessOptions
        )

      val buildMessages = runner.runSbt(
        indicator,
        directory,
        vmExecutable,
        environment,
        sbtLauncher,
        sbtCommandsString,
        SbtBundle.message("sbt.extracting.project.structure.from.sbt"),
        passParentEnvironment,
        context.timingCollector,
        sbtProcessOptions
      )

      copyFileContentsIfNeeded(transferredStructureFile, structureFile)

      extraSbtFileToRemove.foreach { path =>
        try {
          Files.deleteIfExists(path)
        } catch {
          case exc: Throwable =>
            log.warn(s"[sbt import] cannot remove the temporary sbt file in $path ", exc)
        }
      }

      buildMessages
    end dumpFromProcess

    /**
     * @param extraSbtFileToRemove optional path to a temporary sbt file that needs cleanup after the dump.
     *                             Used in the new import logic (sbt 1.5.0+) to remove the temporary plugin file
     *                             created in the global plugin directory.
     */
    private case class StructureDumpConfig(
      sbtCommands: String,
      extraSbtFileToRemove: Option[Path]
    )

    /**
     * Builds config for dumping the project structure in the new import way (without state transformations).
     * 
     * Due to the sbt issue [[https://github.com/sbt/sbt/issues/8570]] when using `--addPluginSbtFile`, the current
     * approach relies on adding an sbt file to the global plugins directory.
     * The same trick is used in [[SbtProcessManager.createShellProcessHandler]] when an sbt version is lower than 1.2.0.
     * This is less safe and a worse approach by design, but it's the only way I managed to come up with
     * to avoid applying the `sbt-structure` plugin via state transformations.
     * 
     * @todo When any sbt version (1.x/2.x) is published with the fix for [[https://github.com/sbt/sbt/issues/8570]],
     *       add a condition to check if the sbt version contains the fix and use the approach with `--addPluginSbtFile`
     *       and `unmanagedJars` instead of the global plugin directory:
     *       {{{
     *       val fileConverter =
     *         if (sbtVersion.isSbt2) "given FileConverter = fileConverter.value"
     *         else ""
     *       val tmpPluginsSbtFile = SbtUtil.createTemporarySbtFile(
     *         raw"""Compile / unmanagedJars ++= {
     *               |$fileConverter
     *               |Seq(file("${normalizedLocalPath(sbtStructureJar)}")).classpath
     *               |}
     *               |""".stripMargin
     *        )
     *       val launcherArgs = Seq(s"-addPluginSbtFile=${tmpPluginsSbtFile.toRealPath()}")
     *       }}}
     */
    private def getDumpProcessArgsForNewImport(
      projectRoot: Path,
      structureFilePath: Path,
      eelDescriptor: EelDescriptor,
      optString: String,
      generateManagedSources: Boolean,
      sbtStructureJar: Path,
      maybePreferScala2Command: String,
      dumpStructureCommand: String,
      sbtVersion: SbtVersion,
      sbtProcessOptions: SbtProcessOptions
    ): StructureDumpConfig = {
      val commands = buildSbtCompositeCommand(maybePreferScala2Command, dumpStructureCommand)

      val parametersList = new ParametersList()
      parametersList.addAll(sbtProcessOptions.allVmOptions*)

      val globalPluginsDir = SbtUtil.globalPluginsDirectory(sbtVersion, parametersList, eelDescriptor)
      if !globalPluginsDir.exists then
        Files.createDirectories(globalPluginsDir)

      val pluginFile = globalPluginsDir / s"idea-structure${Sbt.Extension}"
      // Unfortunately, when using an sbt file in the global plugin directory instead of `--addPluginSbtFile`,
      // the plugin jar cannot be added with `unmanagedJars` settings. The `unmanagedJars` setting is not considered
      // in the global plugin build, which differs from `--addPluginSbtFile`, which behaves more like adding an sbt file as part of the project build.
      val pluginContent = SbtUtil.sbtStructurePluginDeclaration(sbtVersion)
      Files.writeString(pluginFile, pluginContent, StandardCharsets.UTF_8)
      StructureDumpConfig(commands, extraSbtFileToRemove = Some(pluginFile))
    }

    private def getDumpProcessArgsForLegacySbt(
      projectRoot: Path,
      structureFilePath: Path,
      eelDescriptor: EelDescriptor,
      optString: String,
      generateManagedSources: Boolean,
      sbtStructureJar: Path,
      maybePreferScala2Command: String,
      dumpStructureCommand: String,
      sbtVersion: SbtVersion,
      sbtProcessOptions: SbtProcessOptions
    ): StructureDumpConfig = {
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
      StructureDumpConfig(commands, extraSbtFileToRemove = None)
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
