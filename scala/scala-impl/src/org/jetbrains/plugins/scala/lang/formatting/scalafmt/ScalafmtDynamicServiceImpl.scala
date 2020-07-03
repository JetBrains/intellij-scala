package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import java.net.URL

import com.intellij.notification.{Notification, NotificationAction}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.{PersistentStateComponent, State, Storage}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator, Task}
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.annotations.{Nls, NonNls}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.ScalafmtResolveError._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.{ScalafmtVersion, _}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicServiceImpl.{ProgressIndicatorDownloadListener, ServiceState}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtNotifications.FmtVerbosity
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.ScalafmtDynamicDownloader.DownloadProgressListener._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.ScalafmtDynamicDownloader._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.{ScalafmtDynamicDownloader, ScalafmtReflect}
import org.jetbrains.plugins.scala.util.ScalaCollectionsUtil

import scala.beans.BeanProperty
import scala.collection.mutable
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader
import scala.util.Try

@State(
  name = "ScalafmtDynamicService",
  storages = Array(new Storage("scalafmt_dynamic_resolve_cache.xml"))
)
final class ScalafmtDynamicServiceImpl
  extends ScalafmtDynamicService
  with PersistentStateComponent[ScalafmtDynamicServiceImpl.ServiceState] {

  private val Log = Logger.getInstance(this.getClass)

  private val formattersCache: mutable.Map[ScalafmtVersion, ResolveStatus] = ScalaCollectionsUtil.newConcurrentMap

  private val state: ServiceState = new ServiceState
  override def getState: ServiceState = state
  override def loadState(state: ServiceState): Unit = XmlSerializerUtil.copyBean(state, this.state)

  // the method is mainly used for debugging
  override def clearCaches(): Unit = {
    ProjectManager.getInstance().getOpenProjects.foreach { p =>
      ScalafmtDynamicConfigService.instanceIn(p).clearCaches()
    }
    formattersCache.values.foreach {
      case ResolveStatus.Resolved(scalafmt) => scalafmt.classLoader.close()
      case _ =>
    }
    formattersCache.clear()
    state.resolvedVersions.clear()
  }

  // NOTE: instead of returning download-in-progress error we could reuse downloading process and use it's result
  // NOTE: maybe we should set project in dummy state while downloading formatter?
  override def resolve(
    version: ScalafmtVersion,
    downloadIfMissing: Boolean,
    verbosity: FmtVerbosity,
    resolveFast: Boolean = false,
    progressListener: DownloadProgressListener
  ): ResolveResult = {
    val resolveResult = formattersCache.get(version) match {
      case Some(ResolveStatus.Resolved(scalaFmt)) => Right(scalaFmt)
      case _ if resolveFast => Left(ScalafmtResolveError.NotFound(version))
      case Some(ResolveStatus.DownloadInProgress) => Left(ScalafmtResolveError.DownloadInProgress(version))
      case _ =>
        if (state.resolvedVersions.containsKey(version)) {
          val jarUrls = state.resolvedVersions.get(version).map(new URL(_))
          resolveClassPath(version, jarUrls)
        } else if (downloadIfMissing) {
          downloadAndResolve(version, progressListener)
        } else {
          Left(ScalafmtResolveError.NotFound(version))
        }
    }

    if (verbosity == FmtVerbosity.Verbose)
      resolveResult.left.foreach(reportResolveError)

    resolveResult
  }

  private def downloadAndResolve(version: ScalafmtVersion,
                                 listener: DownloadProgressListener = NoopProgressListener): ResolveResult = {
    val downloader = new ScalafmtDynamicDownloader(listener)
    downloader.download(version)
      .left.map(DownloadError.apply)
      .flatMap { case DownloadSuccess(v, jarUrls) =>
        resolveClassPath(v, jarUrls)
      }
  }

  private def resolveClassPath(version: String, jarUrls: Seq[URL]): ResolveResult = {
    Try {
      val classloader = new URLClassLoader(jarUrls, null)
      val scalaFmt = ScalafmtReflect(
        classloader,
        version,
        respectVersion = true
      )
      state.resolvedVersions.put(version, jarUrls.toArray.map(_.toString))
      formattersCache(version) = ResolveStatus.Resolved(scalaFmt)
      scalaFmt
    }.toEither.left.map {
      case e: ReflectiveOperationException =>
        ScalafmtResolveError.CorruptedClassPath(version, jarUrls, e)
      case e =>
        ScalafmtResolveError.UnknownError(version, e)
    }
  }

  private def reportResolveError(error: ScalafmtResolveError): Unit = {
    import ScalafmtNotifications._

    @NonNls val NewLine = "<br>"
    val ColonWithNewLine = ":" + NewLine

    val baseMessage = ScalaBundle.message("scalafmt.resolve.errors.cant.resolve.scalafmt.version", error.version) + ColonWithNewLine

    error match {
      case ScalafmtResolveError.NotFound(version) =>
        // TODO: if reformat action was performed but scalafmt version is not resolve
        //  then we could postpone reformat action after scalafmt is downloaded
        val message = ScalaBundle.message("scalafmt.resolve.errors.version.is.not.downloaded.yet", version)
        displayWarning(message, Seq(new DownloadScalafmtNotificationActon(version, ScalaBundle.message("scalafmt.download"))))
      case ScalafmtResolveError.DownloadInProgress(_) =>
        val errorMessage = baseMessage + " " + ScalaBundle.message("scalafmt.resolve.errors.download.is.in.progress")
        displayError(errorMessage)
      case DownloadError(_, cause) =>
        val errorMessage = baseMessage + " " + ScalaBundle.message("scalafmt.resolve.errors.downloading.error.occurred") + ColonWithNewLine + cause.getMessage
        displayError(errorMessage)
      case ScalafmtResolveError.CorruptedClassPath(version, _, cause) =>
        Log.warn(cause)
        val action = new DownloadScalafmtNotificationActon(version, ScalaBundle.message("scalafmt.resolve.again")) {
          override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
            state.resolvedVersions.remove(version)
            super.actionPerformed(e, notification)
          }
        }
        displayError(baseMessage + " " + ScalaBundle.message("scalafmt.resolve.errors.classpath.is.corrupted"), Seq(action))
      case ScalafmtResolveError.UnknownError(_, cause) =>
        Log.error(cause)
        displayError(baseMessage + " " + ScalaBundle.message("scalafmt.resolve.errors.unknown.error") + ColonWithNewLine + cause.getMessage)
    }
  }

  private class DownloadScalafmtNotificationActon(version: String, @Nls title: String)
    extends NotificationAction(title) {

    override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
      resolveAsync(version, e.getProject, onResolved = {
        case Right(_) => ScalafmtNotifications.displayInfo(ScalaBundle.message("scalafmt.progress.version.was.downloaded", version))
        case _ => // relying on error reporting in resolve method
      })
      notification.expire()
    }
  }

  override def resolveAsync(
    version: ScalafmtVersion,
    project: Project,
    onResolved: Either[ScalafmtResolveError, ScalafmtReflect] => Unit = _ => ()
  ): Unit =
    formattersCache.get(version) match {
      case Some(ResolveStatus.Resolved(fmt)) =>
        invokeLater(onResolved(Right(fmt)))
      case Some(ResolveStatus.DownloadInProgress) =>
        invokeLater(onResolved(Left(ScalafmtResolveError.DownloadInProgress(version))))
      case _ =>
        val isDownloaded = state.resolvedVersions.containsKey(DefaultVersion)
        val title =
          if (isDownloaded) ScalaBundle.message("scalafmt.progress.resolving.scalafmt.version", version)
          else ScalaBundle.message("scalafmt.progress.downloading.scalafmt.version", version)
        val backgroundTask = new Task.Backgroundable(project, title, true) {
          override def run(indicator: ProgressIndicator): Unit = {
            indicator.setIndeterminate(true)
            val progressListener = new ProgressIndicatorDownloadListener(indicator, title)
            val result = try {
              resolve(version, downloadIfMissing = true, FmtVerbosity.Verbose, progressListener = progressListener)
            } catch {
              case pce: ProcessCanceledException =>
                Left(DownloadError(version, pce))
            }
            onResolved(result)
          }
        }

        val cancelText =
          if (isDownloaded) ScalaBundle.message("scalafmt.progress.resolving.scalafmt.version.cancel", version)
          else ScalaBundle.message("scalafmt.progress.downloading.scalafmt.version.cancel", version)
        backgroundTask.setCancelText(cancelText)

        backgroundTask.queue()
    }

  override def ensureVersionIsResolved(
    version: ScalafmtVersion,
    progressListener: DownloadProgressListener
  ): Unit =
    if (!formattersCache.contains(version))
      resolve(version, downloadIfMissing = true, FmtVerbosity.FailSilent, progressListener = progressListener)
}

object ScalafmtDynamicServiceImpl {

  class ServiceState {
    // scalafmt version -> list of classpath jar URLs
    @BeanProperty
    var resolvedVersions: java.util.Map[ScalafmtVersion, Array[String]] = new java.util.TreeMap()
  }

  private class ProgressIndicatorDownloadListener(
    indicator: ProgressIndicator,
    @Nls prefix: String = ""
  ) extends DownloadProgressListener {
    @throws[ProcessCanceledException]
    override def progressUpdate(message: String): Unit = {
      if (!message.isEmpty) {
        //noinspection ReferencePassedToNls,ScalaExtractStringToBundle
        indicator.setText(prefix + ": " + message)
      }
      indicator.checkCanceled()
    }
    @throws[ProcessCanceledException]
    override def doProgress(): Unit =
      indicator.checkCanceled()
  }
}
