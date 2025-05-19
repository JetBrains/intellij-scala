package org.jetbrains.scalaCli.project.importing

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.lang.JavaVersion
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

  override def installCommand(workspace: Path, customJdk: Option[Sdk]): Try[Seq[String]] = {
    val isScalaCliInstalled = ScalaCliUtils.isScalaCliInstalled(workspace)
    if (isScalaCliInstalled) {
      val jdkOptions = customJdk.map(jdk => Seq("--jvm", JavaVersion.tryParse(jdk.getVersionString).feature.toString)).getOrElse(Seq.empty)
      Success(Seq(getScalaCliCommand, "setup-ide", ".") ++ jdkOptions)
    } else {
      Failure(new IllegalStateException("Unable to install BSP, because Scala CLI is not installed"))
    }
  }

  override def getConfigSetup: bspConfigSteps.ConfigSetup = ScalaCliSetup

  private def isScalaCli(directory: Path): Boolean =
    BspUtil.findFileByName(directory, ProjectDefinitionFileName).isDefined
}
