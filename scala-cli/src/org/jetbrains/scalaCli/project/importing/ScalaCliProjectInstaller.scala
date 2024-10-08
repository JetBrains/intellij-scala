package org.jetbrains.scalaCli.project.importing

import org.jetbrains.bsp.BspUtil
import org.jetbrains.bsp.project.BspProjectInstallProvider
import org.jetbrains.bsp.project.importing.bspConfigSteps
import org.jetbrains.bsp.project.importing.bspConfigSteps.ScalaCliSetup
import org.jetbrains.scalaCli.ScalaCliUtils
import org.jetbrains.scalaCli.ScalaCliUtils.getScalaCliCommand
import org.jetbrains.scalaCli.project.ScalaCliProjectUtils.ProjectDefinitionFileName

import java.io.File
import scala.util.{Try, Success, Failure}

class ScalaCliProjectInstaller extends BspProjectInstallProvider {

  override def canImport(workspace: File): Boolean =
    Option(workspace).filter(_.isDirectory).exists(isScalaCli)

  override def serverName: String = "Scala CLI"

  override def installCommand(workspace: File): Try[Seq[String]] = {
    val isScalaCliInstalled = ScalaCliUtils.isScalaCliInstalled(workspace)
    if (isScalaCliInstalled) {
      Success(Seq(getScalaCliCommand, "setup-ide", "."))
    } else {
      Failure(new IllegalStateException("Unable to install BSP, because Scala CLI is not installed"))
    }
  }

  override def getConfigSetup: bspConfigSteps.ConfigSetup = ScalaCliSetup

  private def isScalaCli(directory: File): Boolean =
    BspUtil.findFileByName(directory, ProjectDefinitionFileName).isDefined
}
