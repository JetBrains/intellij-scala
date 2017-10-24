package org.jetbrains.plugins.hydra

import org.jetbrains.plugins.scala.project.{ProjectExt, Version, Versions}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.hydra.settings.HydraApplicationSettings

/**
  * @author Maris Alexandru
  */
object HydraVersions {
  private val MinHydraVersion = "2.11.8"
  private val UnsupportedHydraVersion = "2.12.0"

  def getSupportedScalaVersions(project: Project): Seq[String] = {
    val scalaVersions = for {
      module <- project.scalaModules
      scalaVersion <- module.sdk.compilerVersion
      if scalaVersion != UnsupportedHydraVersion
      version = Version(scalaVersion)
      if version >= Version(MinHydraVersion)
    } yield version.presentation

    scalaVersions.distinct
  }

  def downloadHydraVersions: Array[String] = (Versions.loadHydraVersions ++ HydraApplicationSettings.getInstance().getDownloadedHydraVersions)
    .distinct
    .sortWith(Version(_) >= Version(_))
}
