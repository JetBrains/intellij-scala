package org.jetbrains.scalaCli.project.importing

import com.intellij.openapi.project.Project
import org.jetbrains.bsp.BspUtil
import org.jetbrains.bsp.project.importing.bspConfigSteps
import org.jetbrains.bsp.project.importing.bspConfigSteps.ConfigSetup
import org.jetbrains.bsp.project.importing.setup.{BspConfigSetup, BspSetupProvider}
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.scalaCli.project.ScalaCliProjectUtils.ProjectDefinitionFileName

import java.nio.file.Path

class ScalaCliConfigSetupProvider extends BspSetupProvider {

  override def configSetup: ConfigSetup =
    bspConfigSteps.ScalaCliSetup

  override def canImport(workspace: Path): Boolean =
    Option(workspace).filter(_.isDirectory).exists(isScalaCli)

  override def getBspConfigSetup(workspace: Path): BspConfigSetup =
    new ScalaCliConfigSetup(workspace)

  override def bspBuildFileNames(project: Project): Seq[String] =
    Seq(ProjectDefinitionFileName)

  private def isScalaCli(directory: Path): Boolean =
    BspUtil.directoryContainsFile(directory, ProjectDefinitionFileName)
}

private[scalaCli] object ScalaCliConfigSetupProvider {
  val BundledConfigFileName = "scala.json"
  val StandaloneConfigFileName = "scala-cli.json"
}