package org.jetbrains

import _root_.java.io.File

import _root_.org.jetbrains.plugins.scala.util.TestUtils
import _root_.org.jetbrains.sbt._
import _root_.org.jetbrains.sbt.settings.SbtSystemSettings
import com.intellij.openapi.project.Project

/**
 * Mixin to use with tests involving setup of sbt-launch or/and sbt-structure
 * @author Nikolay Obedin
 * @since 10/19/15.
 */
trait SbtStructureSetup {

  val IvyCacheDir: File = new File(TestUtils.getIvyCachePath)

  def setUpSbtLauncherAndStructure(project: Project): Unit = {
    val systemSettings = SbtSystemSettings.getInstance(project)
    systemSettings.setCustomLauncherEnabled(true)
    systemSettings.setCustomLauncherPath(CustomSbtLauncher.canonicalPath)
    systemSettings.setCustomSbtStructurePath(CustomSbtStructure.canonicalPath)
    Option(System.getProperty("sbt.ivy.home")).foreach { ivyHome =>
      systemSettings.vmParameters += s" -Dsbt.ivy.home=$ivyHome"
    }
  }

  private val LauncherVersion = "0.13.13"
  private val SbtStructureVersion = "6.0.2"
  private val CustomSbtLauncher = IvyCacheDir / "org.scala-sbt" / "sbt-launch" / "jars" / s"sbt-launch-$LauncherVersion.jar"
  private val CustomSbtStructure = IvyCacheDir / "scala_2.10" / "sbt_0.13" / "org.jetbrains" / "sbt-structure-extractor-0-13" / "jars" / s"sbt-structure-extractor-0-13-$SbtStructureVersion.jar"
}
