package org.jetbrains.scalaCli

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.bsp.BspUtil

import java.nio.file.Path

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
    BspUtil.isToolInstalledCheckViaVersion(workspace, "scala-cli")

  /**
   * Returns the command used to invoke Scala CLI commands. It can be:
   *  - Bundled with Scala ≥ 3.5.0: `scala`
   *  - Standalone installation: `scala-cli`
   *  - Unit test mode: `./scala-cli` (there is a script in the test project root, see [[org.jetbrains.scalaCli.project.NewScalaCliProjectWizardTest.installScalaCli]]
   */
  def getScalaCliCommand(scalaCliInstallKind: ScalaCliInstallKind): String =
    if (ApplicationManager.getApplication.isUnitTestMode)
      "./scala-cli"
    else
      scalaCliInstallKind match {
        case ScalaCliInstallKind.Bundled => "scala"
        case ScalaCliInstallKind.Standalone => "scala-cli"
      }

  /**
   * Checks if Scala CLI is bundled with the scala installation by attempting to run `scala version --cli-version`.
   * Since Scala 3.5.0, Scala CLI has been included in the Scala distribution.
   */
  private def isBundledScalaCliInstalled(workspace: Path): Boolean = {
    val work = BspUtil.runCommand(workspace, "scala", "version", "--cli-version", "< /dev/null")
    work.fold(
      exc => {
        log.error(s"The scala version with Scala CLI is not installed in $workspace - ${exc.getMessage}")
        false
      },
      _ => true
    )
  }
}
