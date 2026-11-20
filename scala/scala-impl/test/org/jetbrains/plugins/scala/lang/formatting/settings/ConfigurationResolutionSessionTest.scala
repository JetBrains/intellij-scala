package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.util.{ProgressIndicatorBase, ProgressIndicatorListener}
import com.intellij.testFramework.{EdtTestUtil, HeavyPlatformTestCase}
import org.jetbrains.plugins.scala.lang.formatting.settings.EdtRequestTracker.Request
import org.junit.Assert._

class ConfigurationResolutionSessionTest extends HeavyPlatformTestCase {
  override def runInDispatchThread(): Boolean = false

  def testStaleCompletionCannotReleaseReplacementWorker(): Unit = {
    val session = new ConfigurationResolutionSession
    EdtTestUtil.runInEdtAndWait(() => {
      val first = new EmptyProgressIndicator
      val second = new EmptyProgressIndicator
      var firstRequest: Request = null
      session.start { request =>
        firstRequest = request
        first
      }
      session.start(_ => second)
      assertTrue("Replacing a request must cancel its worker", first.isCanceled)

      session.finish(firstRequest)
      session.cancel()
      assertTrue("A stale completion must not detach the replacement from cancellation", second.isCanceled)
    })
  }

  def testFinishingExpiresCallbacksAndReleasesCompletedWorker(): Unit = {
    val session = new ConfigurationResolutionSession
    EdtTestUtil.runInEdtAndWait(() => {
      val progress = new EmptyProgressIndicator
      var request: Request = null
      session.start { started =>
        request = started
        progress
      }
      session.finish(request)
      var applied = false
      request.runIfCurrent { applied = true }
      assertFalse("Completion must expire late preview and resolution callbacks", applied)

      session.start(_ => new EmptyProgressIndicator)
      session.cancel()
      assertFalse("Later requests must not cancel an already completed worker", progress.isCanceled)
    })
  }

  def testCancellationInvalidatesBeforeNotifyingWorker(): Unit = {
    val session = new ConfigurationResolutionSession
    EdtTestUtil.runInEdtAndWait(() => {
      var applied = false
      session.start { request =>
        val progress = new ProgressIndicatorBase
        new ProgressIndicatorListener {
          override def cancelled(): Unit = request.runIfCurrent { applied = true }
        }.installToProgress(progress)
        progress
      }
      session.cancel()
      assertFalse("A callback delivered during cancellation must already be obsolete", applied)
    })
  }

  def testCancellationBeforeProgressAttachmentCancelsReturnedWorker(): Unit = {
    val session = new ConfigurationResolutionSession
    EdtTestUtil.runInEdtAndWait(() => {
      val progress = new EmptyProgressIndicator
      session.start { _ =>
        session.cancel()
        progress
      }
      assertTrue("Cancellation while starting must also cancel the handle returned afterward", progress.isCanceled)
    })
  }

  def testStartupFailureExpiresRequestAndAllowsRetry(): Unit = {
    val session = new ConfigurationResolutionSession
    EdtTestUtil.runInEdtAndWait(() => {
      var failedRequest: Request = null
      assertThrows("A synchronous startup failure must reach the caller", classOf[IllegalStateException], () => {
        session.start { request =>
          failedRequest = request
          throw new IllegalStateException("Synthetic startup failure")
        }
      })
      var applied = false
      failedRequest.runIfCurrent { applied = true }
      assertFalse("A failed start must expire its request", applied)

      val retried = new EmptyProgressIndicator
      session.start(_ => retried)
      session.cancel()
      assertTrue("A retry after startup failure must be owned and cancellable", retried.isCanceled)
    })
  }

  def testOwnershipChangesRequireEdt(): Unit = {
    val session = new ConfigurationResolutionSession
    assertFalse("Threading contract checks must originate outside EDT", ApplicationManager.getApplication.isDispatchThread)
    assertThrows("Starting a request must reject calls outside EDT", classOf[RuntimeExceptionWithAttachments], () => {
      session.start(_ => new EmptyProgressIndicator)
    })
    val request = EdtTestUtil.runInEdtAndGet(() => {
      var started: Request = null
      session.start { request =>
        started = request
        new EmptyProgressIndicator
      }
      started
    })
    try {
      assertThrows("Finishing a request must reject calls outside EDT", classOf[RuntimeExceptionWithAttachments], () => {
        session.finish(request)
      })
      assertThrows("Cancelling a request must reject calls outside EDT", classOf[RuntimeExceptionWithAttachments], () => {
        session.cancel()
      })
    } finally EdtTestUtil.runInEdtAndWait(() => session.cancel())
  }
}
