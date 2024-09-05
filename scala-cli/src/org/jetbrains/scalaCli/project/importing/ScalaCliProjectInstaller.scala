package org.jetbrains.scalaCli.project.importing

import org.jetbrains.bsp.BspUtil
import org.jetbrains.bsp.project.BspProjectInstallProvider
import org.jetbrains.bsp.project.importing.bspConfigSteps
import org.jetbrains.bsp.project.importing.bspConfigSteps.ScalaCliSetup
import org.jetbrains.scalaCli.ScalaCliUtils
import org.jetbrains.scalaCli.project.ScalaCliProjectUtils.ProjectDefinitionFileName

import java.io.File
import scala.util.Try

class ScalaCliProjectInstaller extends BspProjectInstallProvider {

  override def canImport(workspace: File): Boolean =
    Option(workspace).filter(_.isDirectory).exists(isScalaCli)

  override def serverName: String = "Scala CLI"

  override def installCommand(workspace: File): Try[String] =
    Try(ScalaCliUtils.throwExceptionIfScalaCliNotInstalled(workspace))
      .map(_ => "scala-cli setup-ide .")

  override def getConfigSetup: bspConfigSteps.ConfigSetup = ScalaCliSetup

  private def isScalaCli(directory: File): Boolean =
    BspUtil.findFileByName(directory, ProjectDefinitionFileName).isDefined
}
