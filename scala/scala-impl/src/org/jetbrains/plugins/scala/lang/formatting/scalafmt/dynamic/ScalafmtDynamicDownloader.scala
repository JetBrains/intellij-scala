package org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic

import com.intellij.openapi.progress.ProcessCanceledException
import org.apache.ivy.util.{AbstractMessageLogger, MessageLogger}
import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.ScalafmtDynamicDownloader._
import org.scalafmt.dynamic.{Dependency, ScalafmtVersion}

import java.net.URL
import java.nio.file.Path
import scala.util.control.NonFatal

class ScalafmtDynamicDownloader(
  extraResolvers: Seq[Resolver],
  progressListener: DownloadProgressListener
) {

  def download(version: ScalafmtVersion): Either[DownloadFailure, DownloadSuccess] =
    try {
      val dependencies = Dependency.dependencies(version)
        .map(x => (x.group % x.artifact % x.version).transitive())
      val resolver = new ScalafmtDependencyResolver(extraResolvers, progressListener)
      val resolvedDependencies = resolver.resolve(dependencies: _*)
      val jars: Seq[Path] = resolvedDependencies.map(_.file.toPath)
      val urls = jars.map(_.toUri.toURL)
      Right(DownloadSuccess(version, urls))
    } catch {
      case e: ProcessCanceledException => throw e
      case NonFatal(e) => Left(DownloadFailure(version, e))
    }

}

object ScalafmtDynamicDownloader {
  sealed trait DownloadResult {
    def version: ScalafmtVersion
  }
  case class DownloadSuccess(override val version: ScalafmtVersion, jarUrls: Seq[URL]) extends DownloadResult
  case class DownloadFailure(override val version: ScalafmtVersion, cause: Throwable) extends DownloadResult

  trait DownloadProgressListener {
    def progressUpdate(message: String): Unit
    def doProgress(): Unit = ()
  }

  object DownloadProgressListener {
    val NoopProgressListener: DownloadProgressListener = _ => {}
  }

  private class ScalafmtDependencyResolver(
    extraResolvers: Seq[Resolver],
    progressListener: DownloadProgressListener
  ) extends DependencyManagerBase {

    // first search in the provided resolvers, then fallback to the default ones
    // but only use resolvers with http(s) protocol, to avoid problems with private repositories.
    // (like gcs, s3, etc.) This is fine since scalafmt should always be published to a public repo.
    override protected def resolvers: Seq[Resolver] = {
      val HttpUrl = "(https?://.*)".r
      (extraResolvers ++ super.resolvers).filter {
        case MavenResolver(_, HttpUrl(_)) => true
        case IvyResolver(_, HttpUrl(_))=> true
        case _ => false
      }
    }

    override protected val logLevel: Int = org.apache.ivy.util.Message.MSG_INFO

    override def createLogger: MessageLogger = new AbstractMessageLogger {
      override def doEndProgress(msg: String): Unit = progressListener.progressUpdate(format(msg))
      override def log(msg: String, level: Int): Unit = progressListener.progressUpdate(format(msg))
      override def rawlog(msg: String, level: Int): Unit = ()
      override def doProgress(): Unit = progressListener.doProgress()
    }

    @inline
    private def format(str: String): String = {
      firstLine(str).trim
    }

    private def firstLine(str: String): String = {
      val strTrimmed = str
      val newLineIdx = strTrimmed.indexOf("\n")
      strTrimmed.substring(0, if (newLineIdx != -1) newLineIdx else strTrimmed.length)
    }
  }
}
