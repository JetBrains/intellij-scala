package org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic

import com.intellij.openapi.progress.ProcessCanceledException
import org.apache.ivy.util.{AbstractMessageLogger, MessageLogger}
import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.ScalafmtDynamicDownloader._
import org.scalafmt.dynamic.ScalafmtVersion
import org.scalafmt.interfaces.RepositoryPackage

import java.net.URL
import java.nio.file.Path
import scala.util.control.NonFatal

class ScalafmtDynamicDownloader(
  extraResolvers: Seq[Resolver],
  progressListener: DownloadProgressListener
) {

  def download(version: ScalafmtVersion): Either[DownloadFailure, DownloadSuccess] =
    try {
      val dependencies = scalafmtCoreDependencies(version)
      val resolver = new ScalafmtDependencyResolver(extraResolvers, progressListener)
      val resolvedDependencies = resolver.resolve(dependencies*)
      val jars: Seq[Path] = resolvedDependencies.map(_.file)
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

  private final val LegacyScala213Version = "2.13.11"

  /**
   * Calls `org.scalafmt.dynamic.Dependency.dependencies` reflectively, because it is `private[dynamic]` since scalafmt 3.11.0.
   *
   * @note `Dependency` adds `scala-reflect` of the Scala 2.13 version scalafmt-dynamic is built with,
   *       which also bumps `scala-library`, but old scalafmt versions break on newer standard libraries
   *       (e.g. 2.7.5 fails with `NoSuchElementException` in `scala.meta.tokens.Tokens.last` since Scala 2.13.12).
   *       So for scalafmt 3.8.0 and older (built with Scala 2.13.11 and older),
   *       we keep 2.13.11, which scalafmt-dynamic 3.7.17 used.
   */
  private def scalafmtCoreDependencies(version: ScalafmtVersion): Seq[DependencyDescription] = {
    val (_, packages) = Class.forName("org.scalafmt.dynamic.Dependency")
      .getMethod("dependencies", classOf[ScalafmtVersion])
      .invoke(null, version)
      .asInstanceOf[(String, Seq[RepositoryPackage])]

    def isScalaReflect213(p: RepositoryPackage): Boolean =
      p.group == "org.scala-lang" && p.artifact == "scala-reflect" && p.version.startsWith("2.13.")

    def createDep(p: RepositoryPackage, packageVersion: String): DependencyDescription =
      (p.group % p.artifact % packageVersion).transitive()

    packages.map {
      case p if isScalaReflect213(p) =>
        val useLegacy = version < ScalafmtVersion(3, 8, 1)
        val packageVersion = if useLegacy then LegacyScala213Version else p.version
        createDep(p, packageVersion)
      case p =>
        createDep(p, p.version)
    }
  }

  private class ScalafmtDependencyResolver(
    extraResolvers: Seq[Resolver],
    progressListener: DownloadProgressListener
  ) extends DependencyManagerBase {

    // first search in the provided resolvers, then fallback to the default ones
    // Workaround for SCL-19488:
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
