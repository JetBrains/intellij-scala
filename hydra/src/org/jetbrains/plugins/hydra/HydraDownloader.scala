package org.jetbrains.plugins.hydra

import java.io.File

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.hydra.settings.HydraApplicationSettings
import org.jetbrains.plugins.scala.project.template.{Downloader, FileExt}

/**
  * @author Maris Alexandru
  */
object HydraDownloader {

  import Downloader._

  private val Log = Logger.getInstance(this.getClass)
  private val hydraGlobalSettings = HydraApplicationSettings.getInstance()
  private val SplitRegex = "\\* Attributed\\(|\\)".r
  private val ArtifactsRegex = "\\* Attributed\\(.*\\)".r
  private val GroupId = "com.triplequote"

  def checkIfArtifactsExist(artifacts: List[String]): Boolean = {
    artifacts.forall(new File(_).exists())
  }

  def downloadIfNotPresent(scalaVersion: String, manager: ProgressManager)
                          (hydraVersion: String,
                           repositoryName: String,
                           repositoryURL: String,
                           repositoryRealm: String,
                           login: String,
                           password: String): Unit = {
    val artifacts = hydraGlobalSettings.artifactPaths.get((scalaVersion, hydraVersion))

    if (artifacts.isEmpty) {
      val processAdapter = new DownloadProcessAdapter(manager)
      createTempSbtProject(
        s"${scalaVersion}_$hydraVersion",
        processAdapter,
        setScalaSBTCommand(scalaVersion),
        s"""set credentials := Seq(Credentials("$repositoryRealm", "$repositoryName", "$login", \"\"\"$password\"\"\"))""",
        s"""set resolvers := Seq("Triplequote Plugins Ivy Releases" at "$repositoryURL")""",
        setDependenciesSBTCommand(s""""$GroupId" % "hydra_$scalaVersion" % "$hydraVersion"""", s"""("$GroupId" % "hydra-bridge_1_0" % "$hydraVersion").sources()"""),
        UpdateClassifiersSBTCommand,
        "show dependencyClasspath"
      )

      val text = processAdapter.text()
      Log.info(s"Temp SBT project output: $text")
      cacheArtifacts(text, scalaVersion, hydraVersion)
    }
  }

  private def cacheArtifacts(artifacts: String, scalaVersion: String, hydraVersion: String): Unit = {
    val paths = artifacts.split("\n").filter(s => ArtifactsRegex.findFirstIn(s).nonEmpty).map(s => SplitRegex.split(s)(1)).toList

    if (checkIfArtifactsExist(paths)) {
      val maybeHydraBridge = paths.headOption.flatMap { path =>
        path.split(GroupId).headOption
      }.map { downloadFolder =>
        new File(s"$downloadFolder$GroupId") / "hydra-bridge_1_0" / "srcs" / s"hydra-bridge_1_0-$hydraVersion-sources.jar"
      }

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
