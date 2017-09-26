package org.jetbrains.plugins.scala.caches

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.plugins.scala.project.template.FileExt
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.scala.project.template.Downloader

/**
  * @author Maris Alexandru
  */
object HydraArtifactsCache {
  private val Log = Logger.getInstance(this.getClass)
  private val cache = new ConcurrentHashMap[(String, String), Seq[String]]()
  private val SplitRegex = "\\* Attributed\\(|\\)".r
  private val ArtifactsRegex = "\\* Attributed\\(.*\\)".r
  private val GroupId = "com.triplequote"

  def getOrDownload(scalaVersion: String, hydraVersion: String, listener: (String) => Unit): Seq[String] = {
    val artifacts = cache.getOrDefault((scalaVersion, hydraVersion), Seq.empty)

    if (artifacts.isEmpty) {
      val sbtBufferedOutput = new StringBuilder
      Downloader.downloadHydra(s"${scalaVersion}_$hydraVersion", (text: String) => {
        sbtBufferedOutput.append(text)
        listener(text)
      })
      Log.info("Temp SBT project output: " + sbtBufferedOutput.toString())
      cacheArtifacts(sbtBufferedOutput.toString, scalaVersion, hydraVersion)
    } else {
      artifacts
    }
  }

  private def cacheArtifacts(artifacts: String, scalaVersion: String, hydraVersion: String) = {
    def findDownloadFolder(path: String) = path.split(GroupId).headOption

    val paths = artifacts.split("\n").filter(s => ArtifactsRegex.findFirstIn(s).nonEmpty).map(s => SplitRegex.split(s)(1))
    val maybeHydraBridge = for {
      path  <- paths.headOption
      downloadFolder <- findDownloadFolder(path)
    } yield new File(s"$downloadFolder$GroupId") / "hydra-bridge_1_0" / "srcs" / s"hydra-bridge_1_0-$hydraVersion-sources.jar"

    maybeHydraBridge match {
      case None =>
        Log.warn(s"Hydra bridge $hydraVersion path couldn't be created from $artifacts")
        cache.put((scalaVersion, hydraVersion), paths)
        paths
      case Some(hydraBridge) =>
        val artifactPaths = hydraBridge.getAbsolutePath +: paths
        cache.put((scalaVersion, hydraVersion), artifactPaths)
        artifactPaths
    }
  }
}
