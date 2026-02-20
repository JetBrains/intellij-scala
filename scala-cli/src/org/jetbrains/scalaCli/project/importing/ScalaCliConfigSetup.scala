package org.jetbrains.scalaCli.project.importing

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.bsp.project.importing.setup.CommandBasedBspConfigSetup
import org.jetbrains.scalaCli.ScalaCliUtils
import org.jetbrains.scalaCli.ScalaCliUtils.getScalaCliCommand

import java.nio.file.Path
import scala.util.{Failure, Success, Try}

/** Handles Scala CLI BSP configuration generation. */
final class ScalaCliConfigSetup(workspace: Path) extends CommandBasedBspConfigSetup(workspace) {

  override protected def serverName: String = "Scala CLI"

  override protected def installCommand(workspace: Path, indicator: ProgressIndicator): Try[Seq[String]] =
    ScalaCliUtils.detectScalaCliInstallKind(workspace, indicator) match {
      case Some(scalaCliInstallKind) =>
        Success(getScalaCliCommand(scalaCliInstallKind) ++ Seq("setup-ide", "."))
      case None =>
        Failure(new IllegalStateException("Unable to install BSP, because Scala CLI is not installed"))
    }
}
