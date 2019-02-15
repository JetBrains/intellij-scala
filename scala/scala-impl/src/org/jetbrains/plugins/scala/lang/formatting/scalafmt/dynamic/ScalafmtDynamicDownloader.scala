package org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic

import java.io.PrintWriter
import java.net.URLClassLoader
import java.nio.file.Path

import com.geirsson.coursiersmall._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.ScalafmtDynamicDownloader.DownloadFailure
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.utils.BuildInfo

import scala.concurrent.duration.Duration
import scala.util.Try

class ScalafmtDynamicDownloader(respectVersion: Boolean,
                                downloadProgressWriter: PrintWriter,
                                ttl: Option[Duration] = None) {

  def download(version: String): Either[DownloadFailure, ScalafmtReflect] = {
    Try {
      val settings = new Settings()
        .withDependencies(dependencies(version))
        .withTtl(ttl.orElse(Some(Duration.Inf)))
        .withWriter(downloadProgressWriter)
        .withRepositories(List(
          Repository.MavenCentral,
          Repository.Ivy2Local,
          Repository.SonatypeReleases,
          Repository.SonatypeSnapshots
        ))
      val jars: Seq[Path] = CoursierSmall.fetch(settings)
      val urls = jars.map(_.toUri.toURL).toArray
      val classloader = new URLClassLoader(urls, null)
      ScalafmtReflect(
        classloader,
        version,
        respectVersion
      )
    }.toEither.left.map {
      // TODO: distinguish between these two errors?
      case e: ResolutionException => DownloadFailure(version, e)
      case e => DownloadFailure(version, e)
    }
  }

  private def dependencies(version: String): List[Dependency] = {
    List(
      new Dependency(organization(version), s"scalafmt-cli_${scalaBinaryVersion(version)}", version),
      new Dependency("org.scala-lang", "scala-reflect", scalaVersion(version))
    )
  }

  private def scalaBinaryVersion(version: String): String =
    if (version.startsWith("0.")) "2.11"
    else "2.12"

  private def scalaVersion(version: String): String =
    if (version.startsWith("0.")) BuildInfo.scala211
    else BuildInfo.scala

  private def organization(version: String): String =
    if (version.startsWith("1") || version.startsWith("0") || version == "2.0.0-RC1") {
      "com.geirsson"
    } else {
      "org.scalameta"
    }

}

object ScalafmtDynamicDownloader {
  case class DownloadFailure(version: String, cause: Throwable)
}
