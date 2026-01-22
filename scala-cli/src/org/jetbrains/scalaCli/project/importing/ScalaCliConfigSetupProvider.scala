package org.jetbrains.scalaCli.project.importing

import org.jetbrains.bsp.BspUtil
import org.jetbrains.bsp.project.importing.setup.{BspConfigSetup, ScalaCliSetupProvider}
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.scalaCli.project.ScalaCliProjectUtils.ProjectDefinitionFileName

import java.nio.file.Path

class ScalaCliConfigSetupProvider extends ScalaCliSetupProvider {

  override def canImport(workspace: Path): Boolean =
    Option(workspace).filter(_.isDirectory).exists(isScalaCli)

  override def getBspConfigSetup(workspace: Path): BspConfigSetup =
    new ScalaCliConfigSetup(workspace)

  private def isScalaCli(directory: Path): Boolean =
    BspUtil.findFileByName(directory, ProjectDefinitionFileName).isDefined
}
