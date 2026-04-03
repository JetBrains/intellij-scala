package org.jetbrains.scalaCli.project.importing

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.bsp.project.importing.setup.CommandBasedBspConfigSetup
import org.jetbrains.scalaCli.ScalaCliUtils
import org.jetbrains.scalaCli.ScalaCliUtils.{ScalaCliInstallKind, getScalaCliCommand}

import java.nio.file.Path
import scala.util.{Failure, Success, Try}

/** Handles Scala CLI BSP configuration generation. */
final class ScalaCliConfigSetup(workspace: Path) extends CommandBasedBspConfigSetup(workspace) {

  override protected def serverName: String = "Scala CLI"

  protected type ConnectionTarget = ScalaCliInstallKind

  override protected def resolveConnectionTarget(fileName: String): Option[ScalaCliInstallKind] = fileName match {
    case ScalaCliConfigSetupProvider.BundledConfigFileName => Some(ScalaCliInstallKind.Bundled)
    case ScalaCliConfigSetupProvider.StandaloneConfigFileName => Some(ScalaCliInstallKind.Standalone)
    case _ => None
  }

  override protected def installCommand(workspace: Path, indicator: ProgressIndicator, target: Option[ScalaCliInstallKind]): Try[Seq[String]] =
    detectInstallKind(workspace, indicator, target) match {
      case Right(Some(scalaCliInstallKind)) =>
        Success(getScalaCliCommand(scalaCliInstallKind) ++ Seq("setup-ide", "."))
      case Right(None) =>
        Failure(new IllegalStateException("Unable to install BSP, because Scala CLI is not installed"))
      case Left(exc) =>
        Failure(exc)
    }

  /**
   * Detects which Scala CLI installation is available.
   *
   * If the `targetInstallKind` is specified, it validates that the corresponding installation exists.
   * Returns an error if the requested kind is unavailable.
   */
  private def detectInstallKind(workspace: Path, indicator: ProgressIndicator, targetInstallKind: Option[ScalaCliInstallKind]): Either[Exception, Option[ScalaCliInstallKind]] = {
    val detectedInstallKind = ScalaCliUtils.detectScalaCliInstallKind(workspace, indicator, targetInstallKind)
    (detectedInstallKind, targetInstallKind) match {
      case (None, Some(kind)) =>
        val (toolName, fileName) = kind match {
          case ScalaCliInstallKind.Bundled => ("Scala", ScalaCliConfigSetupProvider.BundledConfigFileName)
          case ScalaCliInstallKind.Standalone => ("Scala CLI", ScalaCliConfigSetupProvider.StandaloneConfigFileName)
        }
        Left(new Exception(s"Unable to detect $toolName installation on machine to generate $fileName BSP connection file"))
      case (result, _) => Right(result)
    }
  }
}
