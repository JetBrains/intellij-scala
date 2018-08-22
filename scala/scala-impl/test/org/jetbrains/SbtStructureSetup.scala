package org.jetbrains

import _root_.java.io.File

import _root_.org.jetbrains.plugins.scala.DependencyManager
import _root_.org.jetbrains.plugins.scala.DependencyManagerBase._
import _root_.org.jetbrains.plugins.scala.util.TestUtils
import _root_.org.jetbrains.sbt.settings.SbtSystemSettings
import _root_.org.jetbrains.plugins.scala.buildinfo.BuildInfo
import com.intellij.openapi.project.Project

/**
 * Utility to use with tests involving setup of sbt-launch or/and sbt-structure
 * @author Nikolay Obedin
 * @since 10/19/15.
 */
object SbtStructureSetup {

  val IvyCacheDir: File = new File(TestUtils.getIvyCachePath)

  def setUpSbtLauncherAndStructure(project: Project): Unit = {
    val launcherVersion: String = BuildInfo.sbtLatestVersion
    val customSbtLauncher = DependencyManager.resolveSingle("org.scala-sbt"  % "sbt-launch" % launcherVersion).file

    assert(customSbtLauncher.isFile, s"sbt launcher not found at $customSbtLauncher")

    val systemSettings = SbtSystemSettings.getInstance(project).getState
    systemSettings.setCustomLauncherEnabled(true)
    systemSettings.setCustomLauncherPath(customSbtLauncher.getCanonicalPath)
    Option(System.getProperty("sbt.ivy.home")).foreach { ivyHome =>
      systemSettings.vmParameters += s" -Dsbt.ivy.home=$ivyHome"
    }
  }
}
