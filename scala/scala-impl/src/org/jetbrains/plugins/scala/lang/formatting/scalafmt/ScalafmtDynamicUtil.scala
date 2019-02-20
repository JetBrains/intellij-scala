package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import java.net.URL

import com.intellij.notification.{Notification, NotificationAction}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicUtil.ScalafmtResolveError.DownloadError
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.ScalafmtDynamicDownloader.DownloadProgressListener._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.ScalafmtDynamicDownloader._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.{ScalafmtDynamicDownloader, ScalafmtReflect}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.ScalaCollectionsUtil

import scala.collection.mutable
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader
import scala.util.Try

// TODO: somehow preserve resolved scalafmt cache between intellij restarts
object ScalafmtDynamicUtil {
  private val Log = Logger.getInstance(this.getClass)

  val DefaultVersion = "1.5.1"
  type ResolveResult = Either[ScalafmtResolveError, ScalafmtReflect]
  type ScalafmtVersion = String

  private val formattersCache: mutable.Map[ScalafmtVersion, ResolveStatus] = ScalaCollectionsUtil.newConcurrentMap

  def isAvailable(version: ScalafmtVersion): Boolean = ???

  // NOTE: instead of returning download-in-progress error we could reuse downloading process and use it's result
  // TODO: maybe we should set project in dummy state while downloading formatter ?
  def resolve(version: ScalafmtVersion,
              downloadIfMissing: Boolean,
              failSilent: Boolean = false,
              progressListener: DownloadProgressListener = NoopProgressListener): ResolveResult = {
    val resolveResult = formattersCache.get(version) match {
      case Some(ResolveStatus.Resolved(scalaFmt)) => Right(scalaFmt)
      case Some(ResolveStatus.DownloadInProgress) => Left(ScalafmtResolveError.DownloadInProgress(version))
      case _ if !downloadIfMissing => Left(ScalafmtResolveError.NotFound(version))
      case _ =>
        downloadAndResolve(version, progressListener).map { scalaFmt =>
          formattersCache(version) = ResolveStatus.Resolved(scalaFmt)
          scalaFmt
        }
    }

    if (!failSilent)
      resolveResult.left.foreach(reportResolveError)

    resolveResult
  }

  private def downloadAndResolve(version: ScalafmtVersion,
                                 listener: DownloadProgressListener = NoopProgressListener): ResolveResult = {
    val downloader = new ScalafmtDynamicDownloader(listener)
    downloader.download(version)
      .left.map(DownloadError)
      .flatMap(resolveClassPath)
  }

  private def resolveClassPath(downloadSuccess: DownloadSuccess): ResolveResult = {
    val DownloadSuccess(version, urls) = downloadSuccess
    Try {
      val classloader = new URLClassLoader(urls, null)
      ScalafmtReflect(
        classloader,
        version,
        respectVersion = true
      )
    }.toEither.left.map {
      case e: ReflectiveOperationException =>
        ScalafmtResolveError.CorruptedClassPath(version, urls, e)
      case e =>
        ScalafmtResolveError.UnknownError(version, e)
    }
  }

  private def reportResolveError(error: ScalafmtResolveError): Unit = {
    import ScalafmtNotifications._

    val baseMessage = s"Can not resolve scalafmt version `${error.version}`:<br>"
    error match {
      case ScalafmtResolveError.NotFound(version) =>
        // TODO: if reformat action was performed but scalafmt version is not resolve
        //  then we should postpone reformat action after scalafmt is downloaded
        val message = s"Scalafmt version `$version` is not downloaded yet.<br>Would you like to to download it?"
        displayWarning(message, Seq(new DownloadScalafmtNotificationActon(version)))
      case ScalafmtResolveError.DownloadInProgress(_) =>
        val errorMessage = s"$baseMessage Download is in progress"
        displayError(errorMessage)
      case DownloadError(failure) =>
        val errorMessage = s"$baseMessage An error occurred during downloading:<br>${failure.cause.getMessage}"
        displayError(errorMessage)
      case ScalafmtResolveError.CorruptedClassPath(_, _, cause) =>
        Log.error(cause)
        displayError(s"$baseMessage Scalafmt classpath is corrupted")
      case ScalafmtResolveError.UnknownError(_, cause) =>
        Log.error(cause)
        displayError(s"$baseMessage Unknown error:<br>${cause.getMessage}")
    }
  }

  private class DownloadScalafmtNotificationActon(version: String) extends NotificationAction("download") {
    override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
      resolveAsync(version, e.getProject, onDownloadFinished = {
        case Right(_) => ScalafmtNotifications.displayInfo(s"Scalafmt version $version was downloaded")
        case _ => // relying on error reporting in resolve method
      })
      notification.expire()
    }
  }

  def resolveAsync(version: ScalafmtVersion, project: ProjectContext,
                   onDownloadFinished: Either[ScalafmtResolveError, ScalafmtReflect] => Unit = _ => ()): Unit = {
    if (formattersCache.contains(version)) return

    val backgroundTask = new Task.Backgroundable(project, s"Downloading scalafmt version `$version`", true) {
      override def run(indicator: ProgressIndicator): Unit = {
        indicator.setFraction(0.0)

        val progressListener: DownloadProgressListener = message => {
          indicator.setText(message)
        }
        val result = resolve(version, downloadIfMissing = true, failSilent = false, progressListener)
        onDownloadFinished(result)

        indicator.setFraction(1.0)
      }
    }
    ProgressManager.getInstance.run(backgroundTask)
  }

  def ensureDefaultVersionIsDownloadedAsync(project: ProjectContext): Unit = {
    if (!formattersCache.contains(DefaultVersion)) {
      resolveAsync(DefaultVersion, project)
    }
  }

  def ensureDefaultVersionIsDownloaded(progressListener: DownloadProgressListener): Unit = {
    if (!formattersCache.contains(DefaultVersion)) {
      resolve(DefaultVersion, downloadIfMissing = true, progressListener = progressListener)
    }
  }

  private sealed trait ResolveStatus
  private object ResolveStatus {
    object DownloadInProgress extends ResolveStatus
    case class Resolved(instance: ScalafmtReflect) extends ResolveStatus
  }

  sealed trait ScalafmtResolveError {
    def version: ScalafmtVersion
  }

  object ScalafmtResolveError {
    case class NotFound(version: ScalafmtVersion) extends ScalafmtResolveError
    case class DownloadInProgress(version: ScalafmtVersion) extends ScalafmtResolveError
    case class DownloadError(failure: DownloadFailure) extends ScalafmtResolveError {
      override def version: ScalafmtVersion = failure.version
    }
    case class CorruptedClassPath(version: ScalafmtVersion, urls: Seq[URL], cause: Throwable) extends ScalafmtResolveError
    case class UnknownError(version: ScalafmtVersion, cause: Throwable) extends ScalafmtResolveError
  }
}
