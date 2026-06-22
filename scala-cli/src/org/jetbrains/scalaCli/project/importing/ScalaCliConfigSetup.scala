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
    ScalaCliUtils.detectScalaCliInstallKind(workspace, indicator, target) match {
      case Some(scalaCliInstallKind) =>
        Success(getScalaCliCommand(scalaCliInstallKind, workspace) ++ Seq("setup-ide", "."))
      case None =>
        Failure(new IllegalStateException("Unable to generate BSP connection file, because Scala CLI is not installed"))
    }
}
