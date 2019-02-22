package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import java.net.URL

import com.intellij.notification.{Notification, NotificationAction}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.{PersistentStateComponent, ServiceManager, State, Storage}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator, Task}
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.ScalafmtResolveError._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService._
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
class ScalafmtDynamicService extends PersistentStateComponent[ScalafmtDynamicService.ServiceState] {
  private val Log = Logger.getInstance(this.getClass)

  private val formattersCache: mutable.Map[ScalafmtVersion, ResolveStatus] = ScalaCollectionsUtil.newConcurrentMap

  private val state: ServiceState = new ServiceState
  override def getState: ServiceState = state
  override def loadState(state: ServiceState): Unit = XmlSerializerUtil.copyBean(state, this.state)

  // the method is mainly used for debugging
  def clearCaches(): Unit = {
    ProjectManager.getInstance().getOpenProjects.foreach { p =>
      ScalafmtDynamicConfigManager.instanceIn(p).clearCaches()
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
  def resolve(version: ScalafmtVersion,
              downloadIfMissing: Boolean,
              failSilent: Boolean = false,
              progressListener: DownloadProgressListener = NoopProgressListener): ResolveResult = {
    val resolveResult = formattersCache.get(version) match {
      case Some(ResolveStatus.Resolved(scalaFmt)) => Right(scalaFmt)
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

    if (!failSilent)
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

    val baseMessage = s"Can not resolve scalafmt version `${error.version}`:<br>"
    error match {
      case ScalafmtResolveError.NotFound(version) =>
        // TODO: if reformat action was performed but scalafmt version is not resolve
        //  then we could postpone reformat action after scalafmt is downloaded
        val message = s"Scalafmt version `$version` is not downloaded yet.<br>Would you like to to download it?"
        displayWarning(message, Seq(new DownloadScalafmtNotificationActon(version)))
      case ScalafmtResolveError.DownloadInProgress(_) =>
        val errorMessage = s"$baseMessage Download is in progress"
        displayError(errorMessage)
      case DownloadError(_, cause) =>
        val errorMessage = s"$baseMessage An error occurred during downloading:<br>${cause.getMessage}"
        displayError(errorMessage)
      case ScalafmtResolveError.CorruptedClassPath(version, _, cause) =>
        Log.warn(cause)
        val action = new DownloadScalafmtNotificationActon(version, title = "resolve again") {
          override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
            state.resolvedVersions.remove(version)
            super.actionPerformed(e, notification)
          }
        }
        displayError(s"$baseMessage Classpath is corrupted", Seq(action))
      case ScalafmtResolveError.UnknownError(_, cause) =>
        Log.error(cause)
        displayError(s"$baseMessage Unknown error:<br>${cause.getMessage}")
    }
  }

  private class DownloadScalafmtNotificationActon(version: String, title: String = "download")
    extends NotificationAction(title) {

    override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
      resolveAsync(version, e.getProject, onDownloadFinished = {
        case Right(_) => ScalafmtNotifications.displayInfo(s"Scalafmt version $version was downloaded")
        case _ => // relying on error reporting in resolve method
      })
      notification.expire()
    }
  }

  def resolveAsync(version: ScalafmtVersion, project: Project,
                   onDownloadFinished: Either[ScalafmtResolveError, ScalafmtReflect] => Unit = _ => ()): Unit = {
    if (formattersCache.contains(version)) return

    val backgroundTask = new Task.Backgroundable(project, s"Downloading scalafmt version $version", true) {
      override def run(indicator: ProgressIndicator): Unit = {
        indicator.setIndeterminate(true)
        val progressListener = new ProgressIndicatorDownloadListener(indicator, "Downloading scalafmt: ")
        val result = try {
          resolve(version, downloadIfMissing = true, failSilent = false, progressListener)
        } catch {
          case pce: ProcessCanceledException =>
            Left(DownloadError(version, pce))
        }
        onDownloadFinished(result)
      }
    }

    backgroundTask
      .setCancelText("Stop downloading")
      .queue()
  }

  def ensureDefaultVersionIsResolvedAsync(project: Project): Unit = {
    if (!state.resolvedVersions.containsKey(DefaultVersion)) {
      resolveAsync(DefaultVersion, project)
    }
  }

  def ensureDefaultVersionIsResolved(progressListener: DownloadProgressListener): Unit = {
    if (!state.resolvedVersions.containsKey(DefaultVersion)) {
      resolve(DefaultVersion, downloadIfMissing = true, progressListener = progressListener)
    }
  }
}

object ScalafmtDynamicService {
  type ScalafmtVersion = String

  val DefaultVersion = "1.5.1"

  def instance: ScalafmtDynamicService = ServiceManager.getService(classOf[ScalafmtDynamicService])

  type ResolveResult = Either[ScalafmtResolveError, ScalafmtReflect]

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
    case class DownloadError(version: String, cause: Throwable) extends ScalafmtResolveError
    case class CorruptedClassPath(version: ScalafmtVersion, urls: Seq[URL], cause: Throwable) extends ScalafmtResolveError
    case class UnknownError(version: ScalafmtVersion, cause: Throwable) extends ScalafmtResolveError

    object DownloadError {
      def apply(f: DownloadFailure): DownloadError = new DownloadError(f.version, f.cause)
    }
  }

  class ServiceState {
    // scalafmt version -> list of classpath jar URLs
    @BeanProperty
    var resolvedVersions: java.util.Map[ScalafmtVersion, Array[String]] = new java.util.TreeMap()
  }


  private class ProgressIndicatorDownloadListener(indicator: ProgressIndicator,
                                                  prefix: String = "") extends DownloadProgressListener {
    @throws[ProcessCanceledException]
    override def progressUpdate(message: String): Unit = {
      indicator.setText(s"$prefix$message")
      indicator.checkCanceled()
    }
    @throws[ProcessCanceledException]
    override def doProgress(): Unit =
      indicator.checkCanceled()
  }
}
