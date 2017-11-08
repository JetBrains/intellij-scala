package org.jetbrains.plugins.hydra

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.scala.project.{ProjectExt, ScalaModule, Version, Versions}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.hydra.compiler.HydraCredentialsManager
import org.jetbrains.plugins.hydra.settings.HydraApplicationSettings

import scala.collection.breakOut
import scala.util.Try

/**
  * @author Maris Alexandru
  */
object HydraVersions {

  private val MinScalaVersion = "2.11.8"
  private val UnsupportedScalaVersion = "2.12.0"
  private val CompilerRegex = """.*scala-compiler-(\d+\.\d+\.\d+)(-SNAPSHOT)?\.jar""".r

  private val Pattern = ".+>(.*\\d+\\.\\d+\\.\\d+.*)/<.*".r
  private val HydraURL = "ivy-releases/com.triplequote/"
  private val MinHydraVersion = Version("0.9.5")
  private val HardcodedHydraVersions = Seq("0.9.5")

  private final val Log: Logger = Logger.getInstance(this.getClass.getName)

  def getSupportedScalaVersions(project: Project): Seq[String] = {
    val scalaModules = project.scalaModules
    // we can't use `module.sdk.compilerVersion` because it assumes the *name* of the Sdk library
    // matches `SDK-2.12.3` or the like. For the Scala project this is simply called `starr`, so we
    // need to look inside and retrieve the actual classpath entries
    val scalaVersionsPerModule: Map[ScalaModule, String] = (for {
      module <- scalaModules
      classpathFile <- module.sdk.compilerClasspath
      mtch <- CompilerRegex.findFirstMatchIn(classpathFile.getName)
      scalaVersion = mtch.group(1)
      if scalaVersion != UnsupportedScalaVersion
      version = Version(scalaVersion)
      if version >= Version(MinScalaVersion)
    } yield module -> version.presentation)(breakOut)

    if (scalaModules.size != scalaVersionsPerModule.size) {
      // we have some modules that don't have a scala version, we should log it
      for (module <- scalaModules.filterNot(scalaVersionsPerModule.contains))
        Log.info(s"Could not retrieve Scala version in module '${module.getName}' with compiler classpath: ${module.sdk.compilerClasspath}")
    }

    scalaVersionsPerModule.values.toSeq.distinct
  }

  def downloadHydraVersions(repoURL: String, login: String, password: String): Array[String] =
    (loadHydraVersions(repoURL, login, password) ++ HydraApplicationSettings.getInstance().hydraVersions)
      .distinct
      .sortWith(Version(_) >= Version(_))

  private def loadHydraVersions(repoURL: String, login: String, password: String): Array[String] = {
    val loadedVersions = loadVersionsForHydra(repoURL, login, password)
    val hydraVersions = loadedVersions.getOrElse(HardcodedHydraVersions)
      .map(Version(_))
      .filter(_ >= MinHydraVersion)
    hydraVersions.map(_.presentation).toArray
  }

  private def loadHydraVersionsFrom(url: String, login:String, password: String, filter: PartialFunction[String, String]): Try[Seq[String]] = {
    val loadedLines = Versions.loadLinesFrom(url) { connection => connection.setRequestProperty("Authorization", "Basic " + HydraCredentialsManager.getBasicAuthEncoding()) }
    loadedLines.map { lines => lines.collect(filter) }
  }

  private def loadVersionsForHydra(repoURL: String, login: String, password: String) = {
    val entityUrl = if (repoURL.endsWith("/")) repoURL + HydraURL else repoURL + "/" + HydraURL

    def downloadHydraVersions(url: String): Seq[String] =
      loadHydraVersionsFrom(url, login, password, { case Pattern(number) => number }).getOrElse(HardcodedHydraVersions).map(Version(_))
        .filter(_ >= MinHydraVersion).map(_.presentation)

    loadHydraVersionsFrom(entityUrl, login, password, {
      case Pattern(number) => number
    }).map { versions =>
      versions.flatMap(version => downloadHydraVersions(s"""$entityUrl$version/""")).distinct
    }
  }
}
