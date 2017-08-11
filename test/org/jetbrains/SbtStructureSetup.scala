package org.jetbrains

import _root_.java.io.File

import _root_.org.jetbrains.plugins.scala.util.TestUtils
import _root_.org.jetbrains.sbt._
import _root_.org.jetbrains.sbt.settings.SbtSystemSettings
import com.intellij.openapi.project.Project
import plugins.scala.buildinfo.BuildInfo

/**
 * Utility to use with tests involving setup of sbt-launch or/and sbt-structure
 * @author Nikolay Obedin
 * @since 10/19/15.
 */
object SbtStructureSetup {

  val IvyCacheDir: File = new File(TestUtils.getIvyCachePath)

  def setUpSbtLauncherAndStructure(project: Project): Unit = {
    val launcherVersion: String = BuildInfo.sbtLatestVersion
    val sbtStructureVersion = BuildInfo.sbtStructureVersion
    val customSbtLauncher = IvyCacheDir / "org.scala-sbt" / "sbt-launch" / "jars" / s"sbt-launch-$launcherVersion.jar"
    val customSbtStructure = IvyCacheDir / "scala_2.10" / "sbt_0.13" / "org.jetbrains" / "sbt-structure-extractor" / "jars" / s"sbt-structure-extractor-$sbtStructureVersion.jar"

    val systemSettings = SbtSystemSettings.getInstance(project)
    systemSettings.setCustomLauncherEnabled(true)
    systemSettings.setCustomLauncherPath(customSbtLauncher.canonicalPath)
    systemSettings.setCustomSbtStructurePath(customSbtStructure.canonicalPath)
    Option(System.getProperty("sbt.ivy.home")).foreach { ivyHome =>
      systemSettings.vmParameters += s" -Dsbt.ivy.home=$ivyHome"
    }
  }
}
