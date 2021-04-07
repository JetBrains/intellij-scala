package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import com.intellij.openapi.application.ApplicationManager

import java.net.URL
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.{ResolveResult, ScalafmtResolveError, ScalafmtVersion}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtNotifications.FmtVerbosity
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.ScalafmtDynamicDownloader.DownloadProgressListener.NoopProgressListener
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.ScalafmtDynamicDownloader._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.ScalafmtReflect

trait ScalafmtDynamicService {
  def clearCaches(): Unit

  def resolve(
    version: ScalafmtVersion,
    downloadIfMissing: Boolean,
    verbosity: FmtVerbosity,
    resolveFast: Boolean = false,
    progressListener: DownloadProgressListener = NoopProgressListener
  ): ResolveResult

  def resolveAsync(
    version: ScalafmtVersion,
    project: Project,
    @RequiresEdt
    onResolved: Either[ScalafmtResolveError, ScalafmtReflect] => Unit = _ => ()
  ): Unit

  def ensureVersionIsResolved(
    version: ScalafmtVersion,
    progressListener: DownloadProgressListener
  ): Unit
}

object ScalafmtDynamicService {

  type ScalafmtVersion = String
  val DefaultVersion = "1.5.1"

  def instance: ScalafmtDynamicService = ApplicationManager.getApplication.getService(classOf[ScalafmtDynamicService])

  type ResolveResult = Either[ScalafmtResolveError, ScalafmtReflect]

  sealed trait ResolveStatus
  object ResolveStatus {
    object DownloadInProgress extends ResolveStatus
    final case class Resolved(instance: ScalafmtReflect) extends ResolveStatus
  }

  sealed trait ScalafmtResolveError {
    def version: ScalafmtVersion
  }

  object ScalafmtResolveError {
    final case class NotFound(override val version: ScalafmtVersion) extends ScalafmtResolveError
    final case class DownloadInProgress(override val version: ScalafmtVersion) extends ScalafmtResolveError
    final case class DownloadError(override val version: String, cause: Throwable) extends ScalafmtResolveError
    final case class CorruptedClassPath(override val version: ScalafmtVersion, urls: Seq[URL], cause: Throwable) extends ScalafmtResolveError
    final case class UnknownError(override val version: ScalafmtVersion, cause: Throwable) extends ScalafmtResolveError

    object DownloadError {
      def apply(f: DownloadFailure): DownloadError = new DownloadError(f.version, f.cause)
    }
  }
}