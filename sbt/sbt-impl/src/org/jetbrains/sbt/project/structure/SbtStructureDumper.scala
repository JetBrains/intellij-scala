//noinspection ApiStatus,UnstableApiUsage
package org.jetbrains.sbt.project.structure

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.ParametersList
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.sbt.process.options.{SbtProcessOptions, SbtProcessOptionsResolver}
import org.jetbrains.sbt.process.{SbtProcessOutputDiagnosticsCollector, SbtRunner}
import org.jetbrains.sbt.project.EelPathKotlinUtils
import org.jetbrains.sbt.project.SbtProjectResolver.ImportContext
import org.jetbrains.sbt.project.settings.SbtExecutionSettings
import org.jetbrains.sbt.shell.communication.{SbtShellBuildMessagesEventProcessor, SbtShellCommandRequest}
import org.jetbrains.sbt.shell.{SbtProcessManager, SbtShellCommunication}
import org.jetbrains.sbt.{Sbt, SbtBundle, SbtUtil, SbtVersion, SbtVersionCapabilities, normalizedLocalPath}

import java.io.IOException
import java.nio.file.{Files, Path}
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

    private val log = Logger.getInstance(getClass)

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
       
       Applied only to sbt 1.5.0+ because:
       - the ability to apply plugin jars via `unmanagedJars` has worked only since sbt 1.3.4.
         I'm not sure which exact change in sbt enabled this, as there were multiple fixes to the `addPluginSbtFile` command.
       - some sbt 1.4.x versions are broken due to missing the necessary files for the arm64 architecture,
         making it difficult to test for other potential issues.
      */
      val isNewImportEnabled =
        sbtVersion >= (if sbtVersion.isSbt2 then SbtVersion("2.0.0-RC9") else SbtVersion("1.5.0"))

      val importId = UUID.randomUUID().toString
      val additionalVmOptionsForNewImport =
        if (isNewImportEnabled)
          Seq(
            s"-Dsbt.structure.options=$optString",
            s"-D$IdeaImportId=$importId"
          )
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

      val StructureDumpConfig(sbtCommandsString, extraSbtFileToRemove, launcherArgs) =
        dumpProcessArgsMethod(
          structureFile,
          context.eelDescriptor,
          optString,
          sbtStructureJar,
          maybePreferScala2Command,
          dumpStructureToCommand,
          sbtVersion,
          sbtProcessOptions,
          project,
          importId
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

      extraSbtFileToRemove.foreach { path =>
        try {
          logPluginFileEvent("pre-remove(eager-cleanup)", importId, path)
          val deleted = Files.deleteIfExists(path)
          logPluginFileEvent(s"removed(eager-cleanup) deleted=$deleted", importId, path)
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
      extraSbtFileToRemove: Option[Path],
      launcherArgs: Seq[String]
    )

    /**
     * Builds config for dumping the project structure in the new import way (without state transformations).
     *
     * Due to the sbt issue [[https://github.com/sbt/sbt/issues/8570]] when using `--addPluginSbtFile` in sbt 1.x < 1.12.1
     * and sbt 2.x < 2.0.0-RC9 the new import relies on adding an sbt file to the global plugins directory.
     * The same trick is used in [[SbtProcessManager.createShellProcessHandler]] when an sbt version is lower than 1.2.0.
     * This is less safe and a worse approach by design, but it's the only way I managed to come up with
     * to avoid applying the `sbt-structure` plugin via state transformations.
     *
     * In sbt 1.x >= 1.12.1 and sbt 2.x >= 2.0.0-RC9, the issue described in [[https://github.com/sbt/sbt/issues/8570]] is fixed, so
     * the approach with `--addPluginSbtFile` can be used.
     */
    private def getDumpProcessArgsForNewImport(
      @unused structureFilePath: Path,
      eelDescriptor: EelDescriptor,
      @unused optString: String,
      sbtStructureJar: Path,
      maybePreferScala2Command: String,
      dumpStructureToCommand: String,
      sbtVersion: SbtVersion,
      sbtProcessOptions: SbtProcessOptions,
      project: Option[Project],
      importId: String
    )(using context: ImportContext): StructureDumpConfig = {
      val commands = buildSbtCompositeCommand(maybePreferScala2Command, dumpStructureToCommand)

      val isAddPluginSbtFileEnabled = sbtVersion.isSbt2 || sbtVersion >= SbtVersion("1.12.1")
      if (isAddPluginSbtFileEnabled) {
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
        StructureDumpConfig(commands, extraSbtFileToRemove = None, launcherArgs)
      } else {
        val parametersList = new ParametersList()
        parametersList.addAll(sbtProcessOptions.allVmOptions *)

        val globalPluginsDir = SbtUtil.globalPluginsDirectory(sbtVersion, parametersList, eelDescriptor)
        if !globalPluginsDir.exists then
          Files.createDirectories(globalPluginsDir)

        val tempPluginFile = eelDescriptor match
          case LocalEelDescriptor.INSTANCE =>
            val f = Files.createTempFile(globalPluginsDir, "idea-structure", Sbt.Extension)
            Runtime.getRuntime.addShutdownHook(Thread(() => {
              logPluginFileEvent("pre-remove(shutdown-hook)", importId, f)
              deleteFileIfExists(f)
              logPluginFileEvent("removed(shutdown-hook)", importId, f)
            }))
            f
          case remote =>
            EelPathKotlinUtils.createTemporaryFile("idea-structure", Sbt.Extension, globalPluginsDir, remote)
        logPluginFileEvent("created", importId, tempPluginFile)

        // Unfortunately, when using an sbt file in the global plugin directory instead of `--addPluginSbtFile`,
        // the plugin jar cannot be added with `unmanagedJars` settings. The `unmanagedJars` setting is not considered
        // in the global plugin build, which differs from `--addPluginSbtFile`, which behaves more like adding an sbt file as part of the project build.
        val pluginContent = createGuardedPluginContent(
          importId, sbtVersion, SbtUtil.sbtStructurePluginDeclaration(sbtVersion, context.repoDir)
        )

        Files.writeString(tempPluginFile, pluginContent)
        logPluginFileEvent("written", importId, tempPluginFile)
        StructureDumpConfig(commands, extraSbtFileToRemove = Some(tempPluginFile), launcherArgs = Nil)
      }
    }

    private def deleteFileIfExists(path: Path): Unit =
      try Files.deleteIfExists(path)
      catch case _: IOException => ()

    /**
     * Diagnostic logging for the lifecycle of the temporary `idea-structure*.sbt` plugin file.
     *
     * @see SCL-25691
     */
    private def logPluginFileEvent(event: String, importId: String, path: Path): Unit =
      try {
        val exists = Try(Files.exists(path)).getOrElse(false)
        log.info(s"[sbt import][idea-structure-plugin] $event | importId=$importId | exists=$exists | path=$path")
      } catch {
        case _: Throwable =>
      }

    private def getDumpProcessArgsForLegacySbt(
      @unused structureFilePath: Path,
      @unused eelDescriptor: EelDescriptor,
      optString: String,
      sbtStructureJar: Path,
      maybePreferScala2Command: String,
      dumpStructureToCommand: String,
      sbtVersion: SbtVersion,
      @unused sbtProcessOptions: SbtProcessOptions,
      @unused project: Option[Project],
      @unused importId: String
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
      StructureDumpConfig(commands, extraSbtFileToRemove = None, launcherArgs = Nil)
    }
  end FromProcess


  /**
   * JVM system property used to identify a specific sbt import.
   * Its value (a unique import id) is passed to the sbt import process and checked inside the generated global plugin file
   * to ensure the plugin settings are activated only for the current import.
   *
   * The idea for this implementation is based on [[org.jetbrains.sbt.shell.process.utils.SpecialSbtVmOptions.IdeaRunIdVmOption]]
   *
   * @see [[createGuardedPluginContent]]
   */
  private val IdeaImportId = "idea.import.id"

  /**
   * Wraps the given sbt settings in a guard condition that activates them only when the sbt import process
   * was launched with the matching `importId` set with the [[IdeaImportId]] system property.
   *
   * This prevents the temporary global plugin file from interfering with other concurrently running imports or sbt sessions.
   */
  @VisibleForTesting
  private[project] def createGuardedPluginContent(importId: String, sbtVersion: SbtVersion, settings: Seq[String]): String = {
    val SeqFqn = SbtVersionCapabilities.collectionsSeqClassFqn(sbtVersion)
    val settingsString = s"$SeqFqn(${settings.mkString(",")})"
    s"""if (java.lang.System.getProperty("$IdeaImportId", "false") == "$importId") { $settingsString } else $SeqFqn.empty"""
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
