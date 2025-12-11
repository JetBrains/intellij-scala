package org.jetbrains.scalaCli

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.bsp.BspUtil

import java.nio.file.Path

object ScalaCliUtils {
  private val log = Logger.getInstance(getClass)

  def isScalaCliInstalled(workspace: Path): Boolean =
    isScalaCliBundled(workspace).nonEmpty

  /**
   * Determines if Scala CLI is bundled with the Scala distribution or installed as a standalone tool.
   *
   * @return Option indicating the status of Scala CLI:
   *         - Some(true) if Scala CLI is bundled with Scala
   *         - Some(false) if Scala CLI is installed standalone
   *         - None if not installed
   */
  def isScalaCliBundled(workspace: Path): Option[Boolean] =
    if (detectBundledScalaCli(workspace))
      Some(true) // bundled with Scala
    else if (BspUtil.checkIfToolIsInstalled(workspace, "scala-cli"))
      Some(false) // standalone installation
    else
      None // not installed

  /**
   * If these are tests, the Scala CLI is not installed globally - the script is only available in the project root directory,
   * so for this reason we have to change the way it is called.
   *
   * For Scala 3.5.0+, Scala CLI is bundled and can be invoked via `scala` command.
   *
   * @param isBundled whether Scala CLI is bundled with Scala distribution
   */
  def getScalaCliCommand(isBundled: Boolean): String =
    if (ApplicationManager.getApplication.isUnitTestMode)
      "./scala-cli"
    else if (isBundled)
      "scala"
    else
      "scala-cli"

  /**
   * Checks if Scala CLI is bundled with the scala installation by attempting to run `scala version --cli-version`.
   * Since Scala 3.5.0, Scala CLI has been included in the Scala distribution.
   */
  private def detectBundledScalaCli(workspace: Path): Boolean = {
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
