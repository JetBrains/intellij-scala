package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.execution.impl.ExpirableTokenProvider
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Expirable
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.scala.extensions.invokeLater

/**
 * Lets only the latest asynchronous request for one UI owner apply its results.
 *
 * Without this guard, an older request can finish after a newer one and overwrite
 * the UI with stale data. A completion can also arrive after the UI has been reset or
 * its owner disposed.
 *
 * The platform's [[ExpirableTokenProvider]] allows several valid tokens in one generation.
 * This adapter combines invalidation and token creation in [[begin]], so starting another
 * request always expires earlier requests.
 * Requests start sequentially on EDT; their asynchronous work may run concurrently.
 *
 * Keep one tracker for the lifetime of a UI owner. For example, changing a selection
 * can start loading data while an earlier selection is still loading:
 * {{{
 * private val requests = new EdtRequestTracker
 *
 * @RequiresEdt
 * def reload(project: Project): Unit = {
 *   val request = requests.begin()
 *   val modality = ModalityState.current()
 *   loadAsync(result => {
 *     request.invokeLaterIfCurrent(modality, project) {
 *       updateUi(result)
 *     }
 *   })
 * }
 * }}}
 * Call [[invalidate]] when an outstanding result no longer applies without a replacement
 * request: for example, when clearing a selection, resetting the view, or disposing
 * its owner. This also invalidates completions already queued for EDT execution.
 *
 * For project-associated updates, [[EdtRequestTracker.Request.invokeLaterIfCurrent]] handles
 * EDT dispatch and checks project disposal. Callers choose modality and manage any additional
 * lifetime checks. When using [[EdtRequestTracker.Request.runIfCurrent]], dispatch to EDT before
 * checking validity so invalidation can take effect while a completion waits in the queue.
 * Invalidation prevents applying a result; the underlying asynchronous work can continue.
 *
 * See `EdtRequestTrackerTest` for executable examples of request replacement,
 * invalidation and the EDT contract. For a concrete use case, see [[ScalaFmtSettingsPanel]]:
 * its `resolutionRequests` field documents a stale-result scenario, covered by
 * `ScalaFmtSettingsPanelResolutionTest`.
 */
private[settings] final class EdtRequestTracker {
  import EdtRequestTracker.Request

  private val tokenProvider = new ExpirableTokenProvider

  /**
   * Invalidates every earlier request and returns a handle for the new current request.
   */
  @RequiresEdt
  def begin(): Request = {
    invalidate()
    new Request(tokenProvider.createExpirable())
  }

  /**
   * Invalidates outstanding requests without starting another one.
   * A later call to [[begin]] can start a new current request.
   */
  @RequiresEdt
  def invalidate(): Unit =
    tokenProvider.invalidateAll()
}

/**
 * Request handles issued by [[EdtRequestTracker]].
 */
private[settings] object EdtRequestTracker {
  /**
   * A handle that remains current until its tracker starts another request or invalidates it.
   * The handle can be passed between threads; guarded actions execute on EDT.
   */
  final class Request private[EdtRequestTracker](token: Expirable) {
    /**
     * Checks validity and immediately executes the action on EDT if this request is current.
     * The check does not consume the request, so it can guard more than one update.
     *
     * @param action evaluated only for a current request; callers must dispatch to EDT before calling
     */
    @RequiresEdt
    def runIfCurrent(action: => Unit): Unit =
      if (!token.isExpired) action

    /**
     * Queues `action` on EDT with the supplied modality and applies it only if
     * this request is still current and `project` has not been disposed.
     *
     * May be called from any thread. Both checks happen when the queued action
     * executes, so invalidating the request or disposing the project while it
     * waits in the queue prevents the update.
     *
     * @param modalityState modality captured when the request was started
     * @param project project whose disposal makes the update irrelevant
     * @param action UI update evaluated on EDT only if both checks pass
     */
    def invokeLaterIfCurrent(
      modalityState: ModalityState,
      project: Project
    )(action: => Unit): Unit =
      invokeLater(modalityState) {
        runIfCurrent {
          if (!project.isDisposed) {
            action
          }
        }
      }
  }
}
