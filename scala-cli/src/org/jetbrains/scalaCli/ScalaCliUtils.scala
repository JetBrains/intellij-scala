package org.jetbrains.scalaCli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.bsp.BspUtil

import java.nio.file.Path
import java.util.concurrent.{TimeUnit, TimeoutException}
import scala.io.Source
import scala.util.Try

private object ScalaCliUtils {
  private val log = Logger.getInstance(getClass)

  def isScalaCliInstalled(workspace: Path): Boolean =
    detectScalaCliInstallKind(workspace).nonEmpty

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
  def detectScalaCliInstallKind(workspace: Path): Option[ScalaCliInstallKind] =
    if (isBundledScalaCliInstalled(workspace))
      Some(ScalaCliInstallKind.Bundled)
    else if (isScalaCliStandaloneInstalled(workspace))
      Some(ScalaCliInstallKind.Standalone)
    else
      None

  private def isScalaCliStandaloneInstalled(workspace: Path): Boolean =
    BspUtil.isToolInstalledCheckViaVersion(workspace, getScalaCliStandaloneCommand)

  /**
   * Returns the command used to invoke Scala CLI commands. It can be:
   *  - Bundled with Scala ≥ 3.5.0: `scala` or `./scala` in the unit test mode
   *  - Standalone installation: `scala-cli` or `./scala-cli` in the unit test mode
   *
   * @see [[getScalaCliStandaloneCommand]]
   * @see [[getScalaStandaloneCommand]]
   */
  def getScalaCliCommand(scalaCliInstallKind: ScalaCliInstallKind): String =
    scalaCliInstallKind match {
      case ScalaCliInstallKind.Bundled => getScalaStandaloneCommand
      case ScalaCliInstallKind.Standalone => getScalaCliStandaloneCommand
    }

  /**
   * The unit test mode requires `./scala-cli`,
   * because instead of a global installation, there is a scala-cli script in the test project root.
   *
   * @see [[org.jetbrains.scalaCli.project.NewScalaCliProjectWizardTestBase.installScalaCli]]
   */
  private def getScalaCliStandaloneCommand: String =
    if (ApplicationManager.getApplication.isUnitTestMode) "./scala-cli"
    else "scala-cli"

  /**
   * The unit test mode requires `./scala`,
   * because instead of a global installation, there is a scala script in the test project root.
   * *
   * @see [[org.jetbrains.scalaCli.project.NewScalaCliProjectWizardTestBase.installScala]]
   * @see [[org.jetbrains.scalaCli.project.NewScalaCliProjectWizardTestBase.createScalaWrapperScript]]
   */
  private def getScalaStandaloneCommand: String =
    if (ApplicationManager.getApplication.isUnitTestMode) "./scala"
    else "scala"

  /**
   * Checks if Scala CLI is bundled with the scala installation by attempting to run `scala -version`.
   * Since Scala 3.5.0, Scala CLI has been included in the Scala distribution.
   */
  private def isBundledScalaCliInstalled(workspace: Path): Boolean = {
    val result = tryCheckScalaVersionWithBundledScalaCli(workspace)
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

  private def tryCheckScalaVersionWithBundledScalaCli(directory: Path): Try[Boolean] =
    for {
      output <- executeScalaVersionCommand(directory)
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
  private def executeScalaVersionCommand(directory: Path): Try[String] =
    Try {
      val commandLine = new GeneralCommandLine(getScalaStandaloneCommand, "-version")
        .withWorkDirectory(directory.toString)
      val process = commandLine.toProcessBuilder.start()
      process.getOutputStream.close()

      val completed = process.waitFor(1, TimeUnit.MINUTES)

      if (!completed) {
        process.destroy()
        throw new TimeoutException("Command 'scala -version' timed out after 2 minutes")
      }

      val stdout = Source.fromInputStream(process.getInputStream).mkString
      val stderr = Source.fromInputStream(process.getErrorStream).mkString
      stdout + stderr
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
