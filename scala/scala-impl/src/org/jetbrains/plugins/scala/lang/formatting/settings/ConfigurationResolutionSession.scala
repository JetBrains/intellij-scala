package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.scala.lang.formatting.settings.EdtRequestTracker.Request

import scala.util.control.NonFatal

/**
 * Owns the current Settings configuration task and the validity of its UI callbacks.
 *
 * Starting a replacement invalidates callbacks before cancelling the old worker. Finishing
 * also expires its request, so late preview or completion callbacks cannot revive it.
 * Preview contents and Swing updates belong to [[ScalaFmtSettingsPanel]], not this session.
 * All ownership changes are confined to EDT; the request can guard callbacks from any thread.
 */
private[settings] final class ConfigurationResolutionSession {
  private val requests = new EdtRequestTracker
  private var active: Option[ActiveRequest] = None

  /**
   * Replaces the current task and starts its worker with a fresh callback guard.
   * A task that finishes or is cancelled before `startTask` returns will not retain its handle.
   */
  @RequiresEdt
  def start(startTask: Request => ProgressIndicator): Unit = {
    cancel()
    val started = new ActiveRequest(requests.begin())
    active = Some(started)
    try {
      val progress = startTask(started.request)
      if (active.contains(started)) started.progress = Some(progress)
      else progress.cancel()
    } catch {
      case NonFatal(error) =>
        if (active.contains(started)) cancel()
        throw error
    }
  }

  /**
   * Releases a completed task and expires its callbacks without cancelling completed work.
   * A stale completion cannot release a newer task's progress handle.
   */
  @RequiresEdt
  def finish(request: Request): Unit = {
    if (active.exists(_.request eq request)) {
      active = None
      requests.invalidate()
    }
  }

  /**
   * Invalidates callbacks and releases ownership before requesting cooperative cancellation.
   * Cancellation may itself deliver a callback, which must already be obsolete at that point.
   */
  @RequiresEdt
  def cancel(): Unit = {
    val previous = active
    active = None
    requests.invalidate()
    previous.flatMap(_.progress).foreach(_.cancel())
  }

  private final class ActiveRequest(val request: Request) {
    var progress: Option[ProgressIndicator] = None
  }
}
