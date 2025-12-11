package org.jetbrains.scalaCli.project.importing

import org.jetbrains.bsp.BspUtil
import org.jetbrains.bsp.project.BspProjectInstallProvider
import org.jetbrains.bsp.project.importing.bspConfigSteps
import org.jetbrains.bsp.project.importing.bspConfigSteps.ScalaCliSetup
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.scalaCli.ScalaCliUtils
import org.jetbrains.scalaCli.ScalaCliUtils.getScalaCliCommand
import org.jetbrains.scalaCli.project.ScalaCliProjectUtils.ProjectDefinitionFileName

import java.nio.file.Path
import scala.util.{Failure, Success, Try}

class ScalaCliProjectInstaller extends BspProjectInstallProvider {

  override def canImport(workspace: Path): Boolean =
    Option(workspace).filter(_.isDirectory).exists(isScalaCli)

  override def serverName: String = "Scala CLI"

  override def installCommand(workspace: Path): Try[Seq[String]] =
    ScalaCliUtils.isScalaCliBundled(workspace) match {
      case Some(isBundled) =>
        Success(Seq(getScalaCliCommand(isBundled), "setup-ide", "."))
      case None =>
        Failure(new IllegalStateException("Unable to install BSP, because Scala CLI is not installed"))
    }

  override def getConfigSetup: bspConfigSteps.ConfigSetup = ScalaCliSetup

  private def isScalaCli(directory: Path): Boolean =
    BspUtil.findFileByName(directory, ProjectDefinitionFileName).isDefined
}
