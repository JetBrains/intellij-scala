package org.jetbrains.plugins.scala.util

import com.intellij.concurrency.JobScheduler
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.startup.ProjectActivity

import java.awt.Event
import java.awt.event.MouseEvent
import java.util.concurrent.{ScheduledFuture, TimeUnit}

final class IdeUIFreezingWatcher extends UIFreezingWatcher {

  /**
   * Checks that the global IDE event queue contains any of the events we are interested in (currently keyboard and
   * mouse events).
   */
  override def hasPendingUserInput: Boolean = {
    val queue = IdeEventQueue.getInstance()
    val userEventIds = Seq(Event.KEY_ACTION, Event.KEY_PRESS, MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_WHEEL)

    userEventIds.exists(queue.peekEvent(_) != null)
  }

  override def dumpThreads(): Unit =
    PerformanceWatcher.getInstance().dumpThreads("scalaEdtFreezing/", false, false)
}

private final class UIFreezingGuardActivity extends ProjectActivity {
  /**
   * An entry point for initialising the application-level service. This method is executed once per project, but
   * the application service initialisation is guaranteed to be executed exactly once, which is what matters to us.
   * Project activities are a useful tool for running a piece of code even when the plugin has been dynamically loaded
   * after the IDE has already been running for some time. This helps us to register our application-level service and
   * to initialise our recurring background task, regardless of how/when the Scala plugin was loaded.
   */
  override def execute(project: Project): Unit = {
    // Application-level services are initialised exactly once.
    ApplicationManager.getApplication.getService(classOf[UIFreezingGuardActivity.AppService])
  }
}

private object UIFreezingGuardActivity {

  /**
   * Schedules a recurring task (only once) which runs on a background thread every 300 milliseconds and checks the IDE
   * global event queue for unprocessed user input events.
   * If such events are detected, it issues a cancellation, to hopefully stop the long-running resolve computation which
   * is blocking the UI thread.
   */
  @Service(Array(Service.Level.APP))
  private final class AppService extends Disposable {
    private final val periodMs = 300
    private var periodicTask: ScheduledFuture[?] =
      if (UIFreezingGuard.pceEnabled)
        JobScheduler.getScheduler.scheduleWithFixedDelay(() => UIFreezingGuard.cancelOnUserInput(), periodMs, periodMs, TimeUnit.MILLISECONDS)
      else
        null

    override def dispose(): Unit = {
      if (periodicTask ne null) {
        periodicTask.cancel(false)
        periodicTask = null
      }
    }
  }
}
