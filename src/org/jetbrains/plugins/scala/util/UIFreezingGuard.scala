package org.jetbrains.plugins.scala.util

import java.awt.Event
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

import com.intellij.concurrency.JobScheduler
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.progress.{EmptyProgressIndicator, ProcessCanceledException, ProgressManager}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.util.UIFreezingGuard._

import scala.util.control.NoStackTrace

/**
  * @author Nikolay.Tropin
  */
class UIFreezingGuard extends ApplicationComponent {

  private object progress extends EmptyProgressIndicator {
    private val counter = new AtomicLong()
    
    def timestamp: Long = counter.get()

    override def start(): Unit = {
      counter.incrementAndGet()
      super.start()
    }

    def cancel(l: Long): Unit = {
      if (timestamp == l) super.cancel()
    }
  }

  private val periodMs = 10

  override def initComponent(): Unit = {
    JobScheduler.getScheduler.scheduleWithFixedDelay(cancelOnUserInput(), periodMs, periodMs, TimeUnit.MILLISECONDS)
  }

  private def cancelOnUserInput(): Unit = {
    val timestamp = progress.timestamp
    if (progress.isRunning && hasPendingUserInput) {
      progress.cancel(timestamp)
    }
  }

  override def disposeComponent(): Unit = {}

  override def getComponentName: String = "UI freezing guard"
}

object UIFreezingGuard {

  //used in macro!
  def withResponsibleUI[T](body: => T): T = {
    val app = ApplicationManager.getApplication
    val progressManager = ProgressManager.getInstance()

    if (!isAlreadyGuarded) {

      if (hasPendingUserInput) throw pceInstance

      val start = System.currentTimeMillis()
      try {
        if (!app.isWriteAccessAllowed && !progressManager.hasProgressIndicator)
          progressManager.runProcess(body, ourProgress)
        else
          body
      } finally {
        dumpThreads(System.currentTimeMillis() - start)
      }
    }
    else body
  }

  //used in macro to reduce number of `withResponsibleUI` calls in the stacktrace
  def isAlreadyGuarded: Boolean = {
    val edt = ApplicationManager.getApplication.isDispatchThread
    edt && ourProgress.isRunning || !edt
  }

  private def dumpThreads(ms: Long): Unit = {
    val threshold = 1000
    if (ms > threshold) {
      PerformanceWatcher.getInstance().dumpThreads("scalaEdtFreezing/", false)
    }
  }

  private def ourProgress = ApplicationManager.getApplication.getComponent(classOf[UIFreezingGuard]).progress

  private def hasPendingUserInput: Boolean = {
    val queue = IdeEventQueue.getInstance()
    val userEventIds = Seq(Event.KEY_ACTION, Event.KEY_PRESS, MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_WHEEL)

    userEventIds.exists(queue.peekEvent(_) != null)
  }

  private val pceInstance = new ProcessCanceledException() with NoStackTrace
}
