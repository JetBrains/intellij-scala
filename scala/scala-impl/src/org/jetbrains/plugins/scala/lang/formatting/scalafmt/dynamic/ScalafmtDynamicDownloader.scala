package org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic

import java.io.PrintWriter
import java.net.URL
import java.nio.file.Path

import org.apache.ivy.util.{AbstractMessageLogger, MessageLogger}
import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.DependencyDescription
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.ScalafmtDynamicDownloader._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.utils.BuildInfo

import scala.util.Try

class ScalafmtDynamicDownloader(progressListener: DownloadProgressListener) {

  def download(version: String): Either[DownloadFailure, DownloadSuccess] = {
    Try {
      val resolver = new ScalafmtDependencyResolver(progressListener)
      val resolvedDependencies = resolver.resolve(dependencies(version): _*)
      val jars: Seq[Path] = resolvedDependencies.map(_.file.toPath)
      val urls = jars.map(_.toUri.toURL).toArray
      DownloadSuccess(version, urls)
    }.toEither.left.map { e =>
      DownloadFailure(version, e)
    }
  }

  private def dependencies(version: String): Seq[DependencyDescription] = {
    List(
      DependencyDescription(organization(version), s"scalafmt-cli_${scalaBinaryVersion(version)}", version),
      DependencyDescription("org.scala-lang", "scala-reflect", scalaVersion(version))
    ).map(_.copy(isTransitive = true))
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
  sealed trait DownloadResult {
    def version: String
  }
  case class DownloadSuccess(version: String, jarUrls: Seq[URL]) extends DownloadResult
  case class DownloadFailure(version: String, cause: Throwable) extends DownloadResult

  abstract class DownloadProgressListener {
    def progressUpdate(message: String): Unit
  }

  object DownloadProgressListener {
    val NoopProgressListener: DownloadProgressListener = _ => {}
  }

  private class ScalafmtDependencyResolver(progressListener: DownloadProgressListener) extends DependencyManagerBase {
    // not to exclude scala-reflect & scala-library
    override protected val artifactBlackList: Set[String] = Set()
    override protected val logLevel: Int = org.apache.ivy.util.Message.MSG_INFO
    override def createLogger: MessageLogger = new AbstractMessageLogger {
      override def doEndProgress(msg: String): Unit = progressListener.progressUpdate(msg)
      override def log(msg: String, level: Int): Unit = progressListener.progressUpdate(msg)
      override def rawlog(msg: String, level: Int): Unit = ()
      override def doProgress(): Unit = ()
    }
  }
}
