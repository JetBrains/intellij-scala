package org.jetbrains.plugins.scala.util

import java.awt.Event
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit

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
    //should be used from EDT only
    var isEnabled = false
  }

  private val periodMs = 10

  override def initComponent(): Unit = {
    JobScheduler.getScheduler.scheduleWithFixedDelay(cancelOnUserInput(), periodMs, periodMs, TimeUnit.MILLISECONDS)
  }

  private def cancelOnUserInput(): Unit = {
    if (progress.isEnabled && hasPendingUserInput) {
      progress.cancel()
    }
  }

  override def disposeComponent(): Unit = {}

  override def getComponentName: String = "UI freezing guard"
}

object UIFreezingGuard {

  //used in macro!
  def withResponsibleUI[T](body: => T): T = {
    if (ApplicationManager.getApplication.isDispatchThread && !progress.isEnabled) {

      if (hasPendingUserInput) throw pceInstance

      val start = System.currentTimeMillis()
      try {
        progress.isEnabled = true
        ProgressManager.getInstance().runProcess(body, progress)
      } finally {
        progress.isEnabled = false
        dumpThreads(System.currentTimeMillis() - start)
      }
    }
    else body
  }

  private def dumpThreads(ms: Long): Unit = {
    val threshold = 1000
    if (ms > threshold) {
      PerformanceWatcher.getInstance().dumpThreads("scalaEdtFreezing/", false)
    }
  }

  private def progress = ApplicationManager.getApplication.getComponent(classOf[UIFreezingGuard]).progress

  private def hasPendingUserInput: Boolean = {
    val queue = IdeEventQueue.getInstance()
    val userEventIds = Seq(Event.KEY_ACTION, Event.KEY_PRESS, MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_WHEEL)

    userEventIds.exists(queue.peekEvent(_) != null)
  }

  private val pceInstance = new ProcessCanceledException() with NoStackTrace
}
