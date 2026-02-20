package org.jetbrains.scalaCli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager}
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.bsp.BspUtil

import java.nio.file.Path
import java.util.concurrent.TimeoutException
import scala.util.Try

private object ScalaCliUtils {
  private val log = Logger.getInstance(getClass)

  /** Checks if Scala CLI is installed, showing a cancelable progress dialog. */
  def isScalaCliInstalled(workspace: Path): Boolean =
    ProgressManager.getInstance.runProcessWithProgressSynchronously(
      () => {
        val indicator = ProgressManager.getInstance.getProgressIndicator
        if (indicator == null) false
        else detectScalaCliInstallKind(workspace, indicator).nonEmpty
      },
      ScalaCliBundle.message("scala.cli.detecting"),
      true,
      null
    )

  /** Describes how Scala CLI is installed on the system. */
  sealed trait ScalaCliInstallKind

  private object ScalaCliInstallKind {
    /** Scala CLI is available as part of the Scala distribution (available since Scala 3.5.0) */
    case object Bundled extends ScalaCliInstallKind

    /** Scala CLI is installed as a separate, standalone tool */
    case object Standalone extends ScalaCliInstallKind
  }
  
  /**
   * Detects how and whether Scala CLI is installed.
   *
   * @param workspace directory in which the installation is checked
   * @return Some([[ScalaCliInstallKind]]) if Scala CLI is installed
   *         or `None` if it's not.
   */
  @RequiresBackgroundThread
  def detectScalaCliInstallKind(workspace: Path, indicator: ProgressIndicator): Option[ScalaCliInstallKind] =
    if (isBundledScalaCliInstalled(workspace, indicator))
      Some(ScalaCliInstallKind.Bundled)
    else if (isScalaCliStandaloneInstalled(workspace, indicator))
      Some(ScalaCliInstallKind.Standalone)
    else
      None

  private def isScalaCliStandaloneInstalled(workspace: Path, indicator: ProgressIndicator): Boolean =
    BspUtil.isToolInstalledCheckViaVersion(workspace, indicator, getScalaCliStandaloneCommand: _*)

  /**
   * Returns the command sequence to execute Scala CLI based on the installation type.
   *
   * @see [[createToolExecutionCommand]]
   * @see [[getScalaCliStandaloneCommand]]
   * @see [[getScalaStandaloneCommand]]
   */
  def getScalaCliCommand(scalaCliInstallKind: ScalaCliInstallKind): Seq[String] =
    scalaCliInstallKind match {
      case ScalaCliInstallKind.Bundled => getScalaStandaloneCommand
      case ScalaCliInstallKind.Standalone => getScalaCliStandaloneCommand
    }

  /**
   * @see [[createToolExecutionCommand]]
   */
  private def getScalaCliStandaloneCommand: Seq[String] =
    createToolExecutionCommand("scala-cli")

  /**
   * Creates a command sequence to execute a tool (`scala-cli` or `scala`).
   *
   * The returned command differs based on the execution environment:
   *  - Unit test mode: Returns `./tool` to invoke a local wrapper script placed in the test project root,
   *    rather than relying on a globally installed tool.
   *  - Windows: Prefix the command with `cmd.exe /c` because tools are often distributed as batch scripts
   *    (e.g., `.bat`) that require a command interpreter to execute.
   *
   * @param tool the name of the tool to execute (`scala-cli` or `scala`)
   * @see [[org.jetbrains.scalaCli.project.NewScalaCliProjectWizardTestBase.createScalaWrapperScript]]
   * @see [[org.jetbrains.scalaCli.project.NewScalaCliProjectWizardTestBase.installScalaCli]]
   */
  private def createToolExecutionCommand(tool: String): Seq[String] = {
    val command =
      if (ApplicationManager.getApplication.isUnitTestMode) s"./$tool"
      else tool

    if (SystemInfo.isWindows) Seq("cmd.exe", "/c", command)
    else Seq(command)
  }

  /**
   * @see [[createToolExecutionCommand]]
   */
  private def getScalaStandaloneCommand: Seq[String] =
    createToolExecutionCommand("scala")

  /**
   * Checks if Scala CLI is bundled with the scala installation by attempting to run `scala -version`.
   * Since Scala 3.5.0, Scala CLI has been included in the Scala distribution.
   */
  private def isBundledScalaCliInstalled(workspace: Path, indicator: ProgressIndicator): Boolean = {
    val result = tryCheckScalaVersionWithBundledScalaCli(workspace, indicator)
    result.fold(
      {
        // It's required for tests, see org.jetbrains.scalaCli.project.NewScalaCliProjectWizardWith_ScalaWithoutScalaCLI
        case e: ParseOutputException if ApplicationManager.getApplication.isUnitTestMode =>
          throw e
        case exc =>
          log.warn(s"The scala version with Scala CLI is not installed in $workspace - ${exc.getMessage}")
          false
      },
      output => output
    )
  }

  private def tryCheckScalaVersionWithBundledScalaCli(directory: Path, indicator: ProgressIndicator): Try[Boolean] =
    for {
      output <- executeScalaVersionCommand(directory, indicator)
      (major, minor, patch) <- parseScalaVersion(output)
    } yield {
      if (major > 3 || (major == 3 && minor >= 5))
        true
      else
        throw ParseOutputException(s"Scala version $major.$minor.$patch is lower than 3.5.0")
    }

  /**
   * Executes 'scala -version' command and returns the output.
   */
  @RequiresBackgroundThread
  private def executeScalaVersionCommand(directory: Path, indicator: ProgressIndicator): Try[String] =
    Try {
      val commandLine = new GeneralCommandLine((getScalaStandaloneCommand:+ "-version"): _*)
        .withWorkDirectory(directory.toString)

      val handler = new CapturingProcessHandler(commandLine)
      val output = handler.runProcessWithProgressIndicator(indicator, 45000) // 45s timeout

      if (output.isTimeout) {
        throw new TimeoutException("Command 'scala -version' timed out after 45s")
      }

      output.getStdout + output.getStderr
    }

  /**
   * Parses a Scala version from `scala -version` command output.
   *
   * Supports two formats:
   * - Scala 3.5.0+: "Scala version (default): 3.7.4"
   * - Scala < 3.5.0: "Scala code runner version 2.12.21"
   *
   * @return Right((major, minor, patch)) or Left(ParseOutputException)
   */
  private def parseScalaVersion(output: String): Try[(Int, Int, Int)] = {
    val newFormatPattern = """Scala version \(default\):\s*(\d+)\.(\d+)\.(\d+).*""".r
    val oldFormatPattern = """Scala code runner version\s+(\d+)\.(\d+)\.(\d+).*""".r

    output.linesIterator.collectFirst {
      case newFormatPattern(major, minor, patch) => (major.toInt, minor.toInt, patch.toInt)
      case oldFormatPattern(major, minor, patch) => (major.toInt, minor.toInt, patch.toInt)
    }.toRight(ParseOutputException(s"Unable to parse Scala version from output: $output")).toTry
  }

  /**
   * Exception thrown when parsing output from `scala -version` command fails.
   *
   * In production mode, this exception is simply ignored - when there is a parsing problem, it is assumed that Scala with the required version is not installed.
   * In test mode, this exception is rethrown to allow more specific test cases that check for particular exceptions and their messages.
   *
   * @see [[org.jetbrains.scalaCli.project.NewScalaCliProjectWizard_ScalaWithoutScalaCLI]]
   */
  private case class ParseOutputException(msg: String) extends Exception(msg)
}
