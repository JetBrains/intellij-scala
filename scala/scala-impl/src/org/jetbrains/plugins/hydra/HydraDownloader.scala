package org.jetbrains.plugins.hydra

import java.io.File

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.hydra.compiler.HydraRepositorySettings
import org.jetbrains.plugins.hydra.settings.HydraApplicationSettings
import org.jetbrains.plugins.scala.project.Platform
import org.jetbrains.plugins.scala.project.template.{Downloader, FileExt}

/**
  * @author Maris Alexandru
  */
object HydraDownloader {

  private val Log = Logger.getInstance(this.getClass)
  private val hydraGlobalSettings = HydraApplicationSettings.getInstance()
  private val SplitRegex = "\\* Attributed\\(|\\)".r
  private val ArtifactsRegex = "\\* Attributed\\(.*\\)".r
  private val GroupId = "com.triplequote"

  def checkIfArtifactsExist(artifacts: List[String]): Boolean = {
    artifacts.forall(new File(_).exists())
  }

  def downloadIfNotPresent(repositorySettings: HydraRepositorySettings, scalaVersion: String, hydraVersion: String, listener: String => Unit): Unit = {
    val artifacts = hydraGlobalSettings.artifactPaths.get((scalaVersion, hydraVersion))

    if (artifacts.isEmpty) {
      val sbtBufferedOutput = new StringBuilder
      HydraDownloader.downloadHydra(repositorySettings, s"${scalaVersion}_$hydraVersion", (text: String) => {
        sbtBufferedOutput.append(text)
        listener(text)
      })
      Log.info("Temp SBT project output: " + sbtBufferedOutput.toString())
      cacheArtifacts(sbtBufferedOutput.toString, scalaVersion, hydraVersion)
    }
  }

  private def cacheArtifacts(artifacts: String, scalaVersion: String, hydraVersion: String) = {
    def findDownloadFolder(path: String) = path.split(GroupId).headOption

    val paths = artifacts.split("\n").filter(s => ArtifactsRegex.findFirstIn(s).nonEmpty).map(s => SplitRegex.split(s)(1)).toList

    if (checkIfArtifactsExist(paths)) {
      val maybeHydraBridge = for {
        path  <- paths.headOption
        downloadFolder <- findDownloadFolder(path)
      } yield new File(s"$downloadFolder$GroupId") / "hydra-bridge_1_0" / "srcs" / s"hydra-bridge_1_0-$hydraVersion-sources.jar"

      maybeHydraBridge match {
        case None =>
          Log.warn(s"Hydra bridge $hydraVersion for $scalaVersion path couldn't be found, therefore the artifacts weren't cached")
        case Some(hydraBridge) =>
          val artifacts = hydraBridge.getAbsolutePath +: paths
          hydraGlobalSettings.artifactPaths = hydraGlobalSettings.artifactPaths + ((scalaVersion, hydraVersion) -> artifacts)
      }
    } else {
      Log.warn(s"Hydra artifacts couldn't be cached for $scalaVersion (scala version), $hydraVersion (hydra version) because one or more artifacts doesn't exist")
    }
  }

  def downloadHydra(repositorySettings: HydraRepositorySettings, version: String, listener: String => Unit): Unit = {
    Downloader.createTempSbtProject(Platform.Scala, version, listener, sbtCommandsForHydra(repositorySettings))
  }

  private def sbtCommandsForHydra(repositorySettings: HydraRepositorySettings)(platform: Platform, version: String) = {
    Seq(
      s"""set scalaVersion := "${version.split("_")(0)}"""",
      s"""set credentials := Seq(Credentials("${repositorySettings.repositoryRealm}", "${repositorySettings.repositoryName}", "${repositorySettings.login}", "${repositorySettings.password}"))""",
      s"""set resolvers := Seq("Triplequote Plugins Ivy Releases" at "${repositorySettings.repositoryURL}")""",
      s"""set libraryDependencies := Seq("com.triplequote" % "hydra_${version.split("_")(0)}" % "${version.split("_")(1)}", ("com.triplequote" % "hydra-bridge_1_0" % "${version.split("_")(1)}").sources())""",
      "updateClassifiers",
      "show dependencyClasspath")
  }
}
