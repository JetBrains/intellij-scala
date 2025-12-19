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
        buildSbtCompositeCommand(Seq(
          "reload",
          setCommands,
          maybePreferScala2Command,
          dumpStructureToCommand,
          s"session clear-all $ideaPortSetting"
        ))
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

      val SeqFqn = SbtVersionCapabilities.collectionsSeqClassFqn(sbtVersion)
      val setCommands = Seq(
        """historyPath := None""",
        s"""shellPrompt := { _ => "" }""",
        s"""${scopedSbtSetting("""SettingKey[_root_.scala.Option[_root_.sbt.File]]("sbtStructureOutputFile")""", "_root_.sbt.Global", sbtVersion)} := _root_.scala.Some(_root_.sbt.file("${normalizedLocalPath(transferredStructureFile)}"))""",
        s"""${scopedSbtSetting("""SettingKey[_root_.java.lang.String]("sbtStructureOptions")""", "_root_.sbt.Global", sbtVersion)} := $optString""",
        s"""${scopedSbtSetting("""SettingKey[_root_.scala.Boolean]("generateManagedSourcesDuringStructureDump")""", "_root_.sbt.Global", sbtVersion)} := $generateManagedSources"""
      ).mkString(s"set $SeqFqn(", ",", ")")

      val maybePreferScala2Command = if (preferScala2) "preferScala2" else ""

      val transferredSbtStructureJar =
        EelPathUtils.transferLocalContentToRemote(sbtStructureJar, TransferTarget.Temporary(directory.eelDescriptor))

      val applyStateTransformersCommand = s"""apply -cp "${normalizedLocalPath(transferredSbtStructureJar)}" "org.jetbrains.sbt.CreateTasks" "sbt.jetbrains.LogDownloadArtifacts""""

      val sbtCommandsString = buildSbtCompositeCommand(Seq(
        setCommands,
        applyStateTransformersCommand,
        maybePreferScala2Command,
        SbtUtil.sbtStructureGlobalCommand("dumpStructure", sbtVersion)
      ))

      val buildMessages = runner.runSbt(
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

      copyFileContentsIfNeeded(transferredStructureFile, structureFile)

      buildMessages
    end dumpFromProcess

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

  private def buildSbtCompositeCommand(commands: Seq[String]): String =
    commands.filter(_.nonEmpty).mkString(";", ";", "")

  private def makeOptionsStringLiteral(options: Seq[String]): String =
    options.mkString("\"", ", ", "\"")

  private def scopedSbtSetting(setting: String, scope: String, sbtVersion: SbtVersion): String =
    val supportsSlashSyntax = SbtVersionCapabilities.isSlashSyntaxSupported(sbtVersion)
    if supportsSlashSyntax then
      s"($scope / $setting)"
    else
      s"$setting in $scope"
