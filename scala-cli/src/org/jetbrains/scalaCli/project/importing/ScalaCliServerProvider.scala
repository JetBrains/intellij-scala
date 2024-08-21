package org.jetbrains.scalaCli.project.importing

import org.jetbrains.bsp.BspUtil
import org.jetbrains.bsp.project.ExternalBspServerProvider
import org.jetbrains.scalaCli.project.ScalaCliProjectUtils.ProjectDefinitionFileName

import java.io.File
import scala.util.{Success, Try}

class ScalaCliServerProvider extends ExternalBspServerProvider {

  override def canImport(workspace: File): Boolean =
    Option(workspace) match {
      case Some(directory) if directory.isDirectory => isScalaCli(directory)
      case _ => false
    }

  private def isScalaCli(directory: File): Boolean =
    BspUtil.findFileByName(directory, ProjectDefinitionFileName).isDefined

  override def serverName: String = "ScalaCLI"

  // TODO handle a case in which scala-cli is not installed on the user's machine
  override def installCommand(workspace: File): Try[String] =
    Success("scala-cli setup-ide .")
}
