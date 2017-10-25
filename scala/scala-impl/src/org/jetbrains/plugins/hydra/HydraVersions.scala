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
  private val compilerRegex = """.*scala-compiler-(\d+\.\d+\.\d+)(-SNAPSHOT)?\.jar""".r

  def getSupportedScalaVersions(project: Project): Seq[String] = {
    // we can't use `module.sdk.compilerVersion` because it assumes the *name* of the Sdk library
    // matches `SDK-2.12.3` or the like. For the Scala project this is simply called `starr`, so we
    // need to look inside and retrieve the actual classpath entries
    val scalaVersions = for {
      module <- project.scalaModules
      classpathFile <- module.sdk.compilerClasspath
      mtch <- compilerRegex.findFirstMatchIn(classpathFile.getName)
      scalaVersion = mtch.group(1)
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
