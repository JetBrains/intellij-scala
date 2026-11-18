package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.mock.MockProject
import com.intellij.openapi.application.{ApplicationManager, ModalityState}
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.{EdtTestUtil, HeavyPlatformTestCase, PlatformTestUtil}
import com.intellij.util.ThrowableRunnable
import org.junit.Assert._

import java.util.concurrent.atomic.AtomicBoolean

class EdtRequestTrackerTest extends HeavyPlatformTestCase {
  override def runInDispatchThread(): Boolean = false

  def testBeginningRequestInvalidatesPreviousRequest(): Unit = {
    // Explicit callbacks keep nested callback methods out of JUnit 3's test discovery.
    EdtTestUtil.runInEdtAndWait(new ThrowableRunnable[Throwable] {
      override def run(): Unit = {
        val tracker = new EdtRequestTracker
        val previous = tracker.begin()
        val current = tracker.begin()
        var updates = Vector.empty[String]

        previous.runIfCurrent { updates :+= "previous" }
        current.runIfCurrent { updates :+= "current" }

        assertEquals("Beginning a request must allow only its result to be applied", Vector("current"), updates)
      }
    })
  }

  def testInvalidationSkipsCompletionAndAllowsAnotherRequest(): Unit = {
    EdtTestUtil.runInEdtAndWait(new ThrowableRunnable[Throwable] {
      override def run(): Unit = {
        val tracker = new EdtRequestTracker
        val request = tracker.begin()
        var applied = false

        tracker.invalidate()
        request.runIfCurrent { applied = true }
        assertFalse("Invalidation without a replacement request must prevent applying the old result", applied)

        tracker.begin().runIfCurrent { applied = true }
        assertTrue("A request started after invalidation must be able to apply its result immediately", applied)
      }
    })
  }

  def testInvokeLaterDispatchesCurrentRequestFromBackgroundThread(): Unit = {
    val request = EdtTestUtil.runInEdtAndGet(() => new EdtRequestTracker().begin())
    val appliedOnEdt = new AtomicBoolean(false)

    assertFalse("The queued update must originate outside EDT in this test", ApplicationManager.getApplication.isDispatchThread)
    request.invokeLaterIfCurrent(ModalityState.nonModal(), getProject) {
      appliedOnEdt.set(ApplicationManager.getApplication.isDispatchThread)
    }

    EdtTestUtil.runInEdtAndWait(() => {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      assertTrue("A current request queued from a background thread must apply its update on EDT", appliedOnEdt.get())
    }, false)
  }

  def testInvalidationSkipsQueuedUpdate(): Unit = {
    val applied = new AtomicBoolean(false)
    EdtTestUtil.runInEdtAndWait(() => {
      val tracker = new EdtRequestTracker
      val request = tracker.begin()
      request.invokeLaterIfCurrent(ModalityState.current(), getProject) {
        applied.set(true)
      }
      assertFalse("Even an update submitted from EDT must remain queued until the current action finishes", applied.get())

      tracker.invalidate()
    }, false)

    EdtTestUtil.runInEdtAndWait(() => {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      assertFalse("Invalidation while an update is queued must prevent applying it", applied.get())
    }, false)
  }

  def testProjectDisposalSkipsQueuedUpdate(): Unit = {
    val applied = new AtomicBoolean(false)
    EdtTestUtil.runInEdtAndWait(() => {
      // A separate lightweight project can be disposed without ending the fixture project's lifetime.
      val project = new MockProject(null, getTestRootDisposable)
      val request = new EdtRequestTracker().begin()
      request.invokeLaterIfCurrent(ModalityState.current(), project) {
        applied.set(true)
      }
      assertFalse("The update must stay queued so the project can be disposed before delivery", applied.get())

      Disposer.dispose(project)
    }, false)

    EdtTestUtil.runInEdtAndWait(() => {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      assertFalse("Project disposal while an update is queued must prevent applying it", applied.get())
    }, false)
  }

  def testBeginningRequestRequiresEdt(): Unit = {
    val tracker = new EdtRequestTracker
    assertRequiresEdt("Beginning a request must reject calls outside EDT") {
      tracker.begin()
    }
  }

  def testInvalidationRequiresEdt(): Unit = {
    val tracker = new EdtRequestTracker
    val request = EdtTestUtil.runInEdtAndGet(() => tracker.begin())
    assertRequiresEdt("Invalidation must reject calls outside EDT") {
      tracker.invalidate()
    }

    EdtTestUtil.runInEdtAndWait(() => {
      var applied = false
      request.runIfCurrent { applied = true }
      assertTrue("Rejected invalidation must leave the current request valid", applied)
    })
  }

  def testApplyingCurrentRequestRequiresEdt(): Unit = {
    val tracker = new EdtRequestTracker
    val request = EdtTestUtil.runInEdtAndGet(() => tracker.begin())
    var applied = false

    assertRequiresEdt("Applying a current request must reject calls outside EDT") {
      request.runIfCurrent { applied = true }
    }
    assertFalse("A rejected completion must not evaluate its action", applied)
  }

  def testApplyingExpiredRequestRequiresEdt(): Unit = {
    val request = EdtTestUtil.runInEdtAndGet(() => {
      val tracker = new EdtRequestTracker
      val request = tracker.begin()
      tracker.invalidate()
      request
    })
    var applied = false

    assertRequiresEdt("Even checking an expired request must reject calls outside EDT") {
      request.runIfCurrent { applied = true }
    }
    assertFalse("An expired request must never evaluate its action", applied)
  }

  private def assertRequiresEdt(message: String)(action: => Unit): Unit = {
    assertFalse("Threading contract checks must originate outside EDT", ApplicationManager.getApplication.isDispatchThread)
    assertThrows(message, classOf[RuntimeExceptionWithAttachments], () => action)
  }
}
