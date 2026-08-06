//noinspection ApiStatus,UnstableApiUsage
package org.jetbrains.sbt.project.structure

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelDescriptor
import org.jetbrains.annotations.{TestOnly, VisibleForTesting}
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}
import org.jetbrains.sbt.process.options.SbtProcessOptionsResolver
import org.jetbrains.sbt.process.{SbtProcessOutputDiagnosticsCollector, SbtRunner}
import org.jetbrains.sbt.project.SbtProjectResolver.ImportContext
import org.jetbrains.sbt.project.settings.SbtExecutionSettings
import org.jetbrains.sbt.shell.communication.{SbtShellBuildMessagesEventProcessor, SbtShellCommandRequest}
import org.jetbrains.sbt.shell.{SbtProcessManager, SbtShellCommunication}
import org.jetbrains.sbt.{SbtBundle, SbtUtil, SbtVersion, SbtVersionCapabilities, normalizedLocalPath}

import java.nio.file.Path
import java.util.UUID
import scala.annotation.unused
import scala.concurrent.Future
import scala.jdk.CollectionConverters.MapHasAsJava
import scala.util.Try

sealed trait SbtStructureDumper:
  protected val processOutputCollector: Option[SbtProcessOutputDiagnosticsCollector] =
    SbtProcessOutputDiagnosticsCollector.createIfEnabled()

  final def processOutput: String = processOutputCollector.fold("")(_.processOutput)

  def cancel(): Unit

object SbtStructureDumper:
  final class FromShell extends SbtStructureDumper:
    // Dumping the sbt project structure from sbt-shell is not cancellable.
    // TODO make it cancelable it should and can be cancelable
    override def cancel(): Unit = ()

    def dumpFromShell(
      project: Project,
      structureFile: Path,
      optString: String,
      reporter: BuildReporter,
      preferScala2: Boolean
    )(using context: ImportContext): Future[BuildMessages] =
      reporter.start()

      val shell = SbtShellCommunication.forProject(project)

      lazy val buildCommand: String = {
        // Re-detect the sbt version at the moment the command is built.
        // Since this is called from within `SbtShellCommunication.processCommand`,
        // #getRunningOrDetectedSbtVersion returns the version currently used in the sbt shell rather than the detected one.
        val currentSbtVersion = shell.getRunningOrDetectedSbtVersion

        context.sbtVersion = currentSbtVersion

        val dumpStructureToCommand = buildDumpStructureToCommand(structureFile, currentSbtVersion)

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
            s"""${scopedSbtSetting("_root_.org.jetbrains.sbt.StructureKeys.sbtStructureOptions", "_root_.sbt.Global", currentSbtVersion)} := $optString""",
          ).mkString(s"set $SeqFqn(", ",", ")")

          buildSbtCompositeCommand(
            "reload",
            setCommands,
            maybePreferScala2Command,
            dumpStructureToCommand,
            s"session clear-all $ideaPortSetting"
          )
        } else {
          // `setSbtStructureOptionsProperty` command override the `sbt.structure.options` system property inside the sbt-structure plugin.
          // Later this property is used to initialize `sbtStructureOptions` setting.
          // `setSbtStructureOptionsProperty` must be executed first to ensure system properties are updated
          // before the `reload` initializes the settings.

          // `session clear-all` is not used with this import method because, effectively, nothing needs to be cleared.
          // When it is used, the message "No session settings defined" is displayed in the shell.
          // If, in the future, any session settings are modified, maybe this should be added again.
          buildSbtCompositeCommand(
            s"setSbtStructureOptionsProperty $optString",
            "reload",
            maybePreferScala2Command,
            dumpStructureToCommand
          )
        }
      }

      val aggregator = SbtShellBuildMessagesEventProcessor.forSync(
        project,
        reporter,
        EventId(s"dump:${UUID.randomUUID()}"),
        processOutputCollector,
        startMessage = SbtBundle.message("sbt.extracting.project.structure.from.sbt.shell"),
        finishMessage = SbtBundle.message("sbt.project.structure.extracted")
      )

      val isSbtVersionOutdated = SbtProcessManager.forProject(project).isSbtVersionOutdated
      val terminationMessage = "Sbt shell terminated before sync command is finished"
      val request = SbtShellCommandRequest(buildCommand, aggregator, Some(terminationMessage))
        .withQueuedOutputMirroring()
        .withSbtShellToolWindowActivationOnStartup(enabled = false)
      if isSbtVersionOutdated then
        shell.runAfterSoftRestart(request)
      else
        shell.run(request)

  end FromShell

  final class FromProcess extends SbtStructureDumper:
    private val runner: SbtRunner = SbtRunner(processOutputCollector)

    override def cancel(): Unit = runner.cancel()

    //noinspection ApiStatus,UnstableApiUsage
    def dumpFromProcess(
      indicator: ProgressIndicator,
      directory: Path,
      structureFile: Path,
      optString: String,
      vmExecutable: Path,
      vmOptions: Seq[String],
      sbtOptions: SbtExecutionSettings.SbtOptions,
      environment: Map[String, String],
      sbtLauncher: Path,
      sbtStructureJar: Path,
      preferScala2: Boolean,
      passParentEnvironment: Boolean,
      project: Option[Project]
    )(using reporter: BuildReporter, context: ImportContext): Try[BuildMessages] =
      val sbtVersion = SbtUtil.detectSbtVersion(directory, sbtLauncher)

      val maybePreferScala2Command = if (preferScala2) "preferScala2" else ""

      val dumpStructureToCommand = buildDumpStructureToCommand(structureFile, sbtVersion)

      val sbtTaskTimingOption =
        if (context.timingCollector.nonEmpty) Seq("-Dsbt.task.timings=true")
        else Nil

      /*
       The new import logic:
       - Passes additional system properties to the `sbt-structure` plugin (e.g., `sbt.structure.options`), which are later used to initialize some settings.
         This avoids state transformations by replacing `set` commands.
       - Does not set `historyPath := None` — I don't see any advantage in using this setting.
         Since sbt 1.4.0+, commands prepended with a space are not added to the history, which should be sufficient for this kind of import.
       - Does not set `shellPrompt := { _ => "" }` — from what I've observed, setting `shellPrompt` to `""` prevents the
         prompt from being displayed before shutting down the sbt process (only a cosmetic issue).
       
       Enabled only for sbt 1.x from 1.12.1 and sbt 2.x from 2.0.0-RC9. In principle, applying plugin jars via
       `unmanagedJars` has been possible since sbt 1.3.4, but the `addPluginSbtFile` command had a bug: sbt could not start
       when a build depended on another build that did not have a project directory (https://github.com/sbt/sbt/issues/8570). This bug is
       fixed only in sbt 1.12.1+ and 2.0.0-RC9+, so the new import is enabled only for those versions.
      */
      val isNewImportEnabled =
        sbtVersion >= (if sbtVersion.isSbt2 then SbtVersion("2.0.0-RC9") else SbtVersion("1.12.1"))

      val additionalVmOptionsForNewImport =
        if isNewImportEnabled then Seq(s"-Dsbt.structure.options=$optString")
        else Nil

      val sbtProcessOptions = SbtProcessOptionsResolver.resolveForSeparateProcess(
        directory,
        vmOptions ++ additionalVmOptionsForNewImport ++ sbtTaskTimingOption,
        sbtOptions.options,
        EnvironmentVariablesData.create(environment.asJava, passParentEnvironment),
        additionalLauncherArgs = Nil,
        malformedSbtOptionsFromSettings = sbtOptions.malformedOptions
      )

      val dumpProcessArgsMethod =
        if (isNewImportEnabled) getDumpProcessArgsForNewImport
        else getDumpProcessArgsForLegacySbt

      val (sbtCommandsString, launcherArgs) =
        dumpProcessArgsMethod(
          context.eelDescriptor,
          optString,
          sbtStructureJar,
          maybePreferScala2Command,
          dumpStructureToCommand,
          sbtVersion,
          project,
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
        sbtProcessOptions.copy(sbtLauncherArgs = sbtProcessOptions.sbtLauncherArgs ++ launcherArgs),
        project = project,
      )

      buildMessages
    end dumpFromProcess

    private type StructureDumpConfig = (sbtCommands: String, launcherArgs: Seq[String])

    /**
     * Builds config for dumping the project structure in the new import way (without state transformations).
     */
    private def getDumpProcessArgsForNewImport(
      eelDescriptor: EelDescriptor,
      @unused optString: String,
      sbtStructureJar: Path,
      maybePreferScala2Command: String,
      dumpStructureToCommand: String,
      sbtVersion: SbtVersion,
      project: Option[Project]
    ): StructureDumpConfig = {
      val commands = buildSbtCompositeCommand(maybePreferScala2Command, dumpStructureToCommand)

      val fileConverter =
        if (sbtVersion.isSbt2) "given FileConverter = fileConverter.value"
        else ""

      val tmpPluginsSbtFile = SbtUtil.createTemporarySbtFile(
        raw"""Compile / unmanagedJars ++= {
             |$fileConverter
             |Seq(file("${sbtStructureJar.normalizedLocalPath}")).classpath
             |}
             |""".stripMargin,
        eelDescriptor,
        project
      )

      val launcherArgs = Seq(s"-addPluginSbtFile=${tmpPluginsSbtFile.normalizedLocalPath}")
      (sbtCommands = commands, launcherArgs = launcherArgs)
    }

    private def getDumpProcessArgsForLegacySbt(
      @unused eelDescriptor: EelDescriptor,
      optString: String,
      sbtStructureJar: Path,
      maybePreferScala2Command: String,
      dumpStructureToCommand: String,
      sbtVersion: SbtVersion,
      @unused project: Option[Project]
    ): StructureDumpConfig = {
      val SeqFqn = SbtVersionCapabilities.collectionsSeqClassFqn(sbtVersion)
      val setCommands = Seq(
        """historyPath := None""",
        s"""shellPrompt := { _ => "" }""",
        s"""${scopedSbtSetting("""SettingKey[_root_.java.lang.String]("sbtStructureOptions")""", "_root_.sbt.Global", sbtVersion)} := $optString""",
      ).mkString(s"set $SeqFqn(", ",", ")")

      val applyStateTransformersCommand = s"""apply -cp "${sbtStructureJar.normalizedLocalPath}" "org.jetbrains.sbt.CreateTasks" "sbt.jetbrains.LogDownloadArtifacts""""

      val commands = buildSbtCompositeCommand(
        setCommands,
        applyStateTransformersCommand,
        maybePreferScala2Command,
        dumpStructureToCommand
      )
      (sbtCommands = commands, launcherArgs = Seq.empty[String])
    }
  end FromProcess

  /**
   * Builds the guarded plugin-file content that older plugin versions used to write into sbt's global plugins directory.
   *
   * The current import no longer relies on such files. This is kept only for tests, to simulate a stale file hanging
   * in the global plugins directory and to verify that it does not break the current import. The settings are guarded by
   * the `idea.import.id` system property, which the current import never sets.
   */
  @VisibleForTesting
  @TestOnly
  private[project] def createGuardedPluginContent(importId: String, sbtVersion: SbtVersion, settings: Seq[String]): String = {
    val SeqFqn = SbtVersionCapabilities.collectionsSeqClassFqn(sbtVersion)
    val settingsString = s"$SeqFqn(${settings.mkString(",")})"
    s"""if (java.lang.System.getProperty("idea.import.id", "false") == "$importId") { $settingsString } else $SeqFqn.empty"""
  }

  private def buildSbtCompositeCommand(commands: String*): String =
    commands.filter(_.nonEmpty).mkString(";", ";", "")

  private def buildDumpStructureToCommand(structureFile: Path, sbtVersion: SbtVersion): String =
    s"""${SbtUtil.sbtStructureGlobalCommand("dumpStructureTo", sbtVersion)} "${structureFile.normalizedLocalPath}""""

  private def scopedSbtSetting(setting: String, scope: String, sbtVersion: SbtVersion): String =
    val supportsSlashSyntax = SbtVersionCapabilities.isSlashSyntaxSupported(sbtVersion)
    if supportsSlashSyntax then
      s"($scope / $setting)"
    else
      s"$setting in $scope"
end SbtStructureDumper
