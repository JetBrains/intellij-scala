package org.jetbrains.scalaCli.project.importing

import org.jetbrains.bsp.BspUtil
import org.jetbrains.bsp.project.BspProjectInstallProvider
import org.jetbrains.bsp.project.importing.bspConfigSteps
import org.jetbrains.bsp.project.importing.bspConfigSteps.ScalaCliSetup
import org.jetbrains.scalaCli.project.ScalaCliProjectUtils.ProjectDefinitionFileName

import java.io.File
import scala.util.{Success, Try}

class ScalaCliProjectInstaller extends BspProjectInstallProvider {

  override def canImport(workspace: File): Boolean =
    Option(workspace) match {
      case Some(directory) if directory.isDirectory => isScalaCli(directory)
      case _ => false
    }

  override def serverName: String = "Scala CLI"

  // TODO handle a case in which scala-cli is not installed on the user's machine
  override def installCommand(workspace: File): Try[String] =
    Success("scala-cli setup-ide .")

  override def getConfigSetup: bspConfigSteps.ConfigSetup = ScalaCliSetup

  private def isScalaCli(directory: File): Boolean =
    BspUtil.findFileByName(directory, ProjectDefinitionFileName).isDefined
}
