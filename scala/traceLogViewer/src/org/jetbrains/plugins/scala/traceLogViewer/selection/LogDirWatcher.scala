package org.jetbrains.plugins.scala.traceLogViewer.selection

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.scala.extensions.*
import org.jetbrains.plugins.scala.traceLogger.TraceLog
import org.jetbrains.plugins.scala.util.UnloadAwareDisposable

import java.io.IOException
import java.nio.file.{ClosedWatchServiceException, FileSystems, Files, WatchService}
import scala.concurrent.duration.DurationInt

object LogDirWatcher {
  private val REFRESH_DELAY = 1.second
  private val dir = TraceLog.loggerOutputPath
  private val LOG = Logger.getInstance(getClass)
  private var service: Option[WatchService] = None
  private val refreshTimer = new LastTaskTimer

  def start(): Unit = synchronized {
    if (service.nonEmpty) {
      return
    }

    try {
      import java.nio.file.StandardWatchEventKinds.*

      Files.createDirectories(dir)

      val service = FileSystems.getDefault.newWatchService()
      dir.register(service, ENTRY_CREATE, ENTRY_MODIFY)
      val thread = new Thread(() => run(service))

      this.service = Some(service)
      thread.start()
    } catch {
      case e: IOException =>
        LOG.warn(s"Couldn't start watching TraceLogger directory $dir", e)
    }

    invokeOnDispose(UnloadAwareDisposable.scalaPluginDisposable) {
      stop()
    }
  }

  def stop(): Unit = synchronized {
    service.foreach(_.close())
  }

  private def run(service: WatchService): Unit = {
    while (true) {
      val key = try {
        service.take()
      } catch {
        case _: ClosedWatchServiceException =>
          return
        case e: IOException =>
          LOG.warn(s"Stop watching $dir because of ", e)
          return
      }

      if (!key.pollEvents().isEmpty) {
        refreshTimer.schedule(REFRESH_DELAY) {
          TraceLogSelectionView.refresh(openNewItem = true)
        }
      }

      if (!key.reset()) {
        LOG.warn(s"Continue watching $dir is illegal... stopping.")
      }
    }
  }
}
