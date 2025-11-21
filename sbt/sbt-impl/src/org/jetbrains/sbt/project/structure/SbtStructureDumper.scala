package org.jetbrains.sbt.project.structure

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}
import org.jetbrains.sbt.process.{ProcessOutputCollector, SbtRunner}
import org.jetbrains.sbt.shell.{SbtProcessManager, SbtShellCommunication}
import org.jetbrains.sbt.{SbtBundle, SbtUtil, SbtVersion, SbtVersionCapabilities}

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
      sbtVersion: SbtVersion,
      structureFilePath: String,
      options: Seq[String],
      reporter: BuildReporter,
      preferScala2: Boolean,
      generateManagedSources: Boolean
    ): Future[BuildMessages] =
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
      val optProcessOutputBuilder = processOutputCollector.map(_.processOutputBuilder)
      val aggregator = shell.messageAggregatorForSync(
        reporter,
        EventId(s"dump:${UUID.randomUUID()}"),
        optProcessOutputBuilder,
        startMessage = SbtBundle.message("sbt.extracting.project.structure.from.sbt.shell"),
        finishMessage = SbtBundle.message("sbt.project.structure.extracted")
      )

      val isSbtVersionOutdated = SbtProcessManager.forProject(project).isSbtVersionOutdated
      if isSbtVersionOutdated then
        shell.commandAfterSoftRestart(sbtCommand, BuildMessages.empty, aggregator)
      else
        shell.command(sbtCommand, BuildMessages.empty, aggregator)

  end FromShell

  final class FromProcess extends SbtStructureDumper:
    private val runner: SbtRunner = SbtRunner(processOutputCollector)

    override def cancel(): Unit = runner.cancel()

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
    )(using reporter: BuildReporter): Try[BuildMessages] =
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

      runner.runSbt(
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

  end FromProcess

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
