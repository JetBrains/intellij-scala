package org.jetbrains.scalaCli.project.importing

import com.intellij.openapi.progress.ProgressIndicator
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

  override def installCommand(workspace: Path, indicator: ProgressIndicator): Try[Seq[String]] =
    ScalaCliUtils.detectScalaCliInstallKind(workspace, indicator) match {
      case Some(scalaCliInstallKind) =>
        Success(getScalaCliCommand(scalaCliInstallKind) ++ Seq("setup-ide", "."))
      case None =>
        Failure(new IllegalStateException("Unable to install BSP, Scala CLI installation could not be determined"))
    }

  override def getConfigSetup: bspConfigSteps.ConfigSetup = ScalaCliSetup

  private def isScalaCli(directory: Path): Boolean =
    BspUtil.findFileByName(directory, ProjectDefinitionFileName).isDefined
}
