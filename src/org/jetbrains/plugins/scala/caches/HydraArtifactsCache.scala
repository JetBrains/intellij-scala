package org.jetbrains.plugins.scala.caches

import java.io.File

import org.jetbrains.plugins.scala.project.template.FileExt
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.scala.project.template.Downloader
import org.jetbrains.plugins.scala.settings.HydraApplicationSettings

/**
  * @author Maris Alexandru
  */
object HydraArtifactsCache {
  private val Log = Logger.getInstance(this.getClass)
  private val hydraGlobalSettings = HydraApplicationSettings.getInstance()
  private val SplitRegex = "\\* Attributed\\(|\\)".r
  private val ArtifactsRegex = "\\* Attributed\\(.*\\)".r
  private val GroupId = "com.triplequote"

  def checkIfArtifactsExist(artifacts: List[String]): Boolean = {
    artifacts.forall(new File(_).exists())
  }

  def downloadIfNotPresent(scalaVersion: String, hydraVersion: String, listener: String => Unit): Unit = {
    val artifacts = hydraGlobalSettings.artifactPaths.get((scalaVersion, hydraVersion))

    if (artifacts.isEmpty) {
      val sbtBufferedOutput = new StringBuilder
      Downloader.downloadHydra(s"${scalaVersion}_$hydraVersion", (text: String) => {
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
}
