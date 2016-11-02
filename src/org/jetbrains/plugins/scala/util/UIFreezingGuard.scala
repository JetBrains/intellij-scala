package org.jetbrains.plugins.scala.util

import java.awt.Event
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

import com.intellij.concurrency.JobScheduler
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.{ApplicationManager, ModalityState}
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.progress._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.util.UIFreezingGuard._

import scala.util.control.NoStackTrace

/**
  * @author Nikolay.Tropin
  */
class UIFreezingGuard extends ApplicationComponent {

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
    if (!isAlreadyGuarded) {
      val start = System.currentTimeMillis()
      try {
        val progressManager = ProgressManager.getInstance()
        if (!ApplicationManager.getApplication.isWriteAccessAllowed && !progressManager.hasProgressIndicator) {

          if (hasPendingUserInput)
            throw pceInstance

          progressManager.runProcess(body, progress)
        }
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
    edt && progress.isRunning || !edt
  }

  private def dumpThreads(ms: Long): Unit = {
    val threshold = 1000
    if (ms > threshold) {
      PerformanceWatcher.getInstance().dumpThreads("scalaEdtFreezing/", false)
    }
  }

  private def hasPendingUserInput: Boolean = {
    val queue = IdeEventQueue.getInstance()
    val userEventIds = Seq(Event.KEY_ACTION, Event.KEY_PRESS, MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_WHEEL)

    userEventIds.exists(queue.peekEvent(_) != null)
  }

  private val pceInstance = new ProcessCanceledException() with NoStackTrace {
    override def getMessage: String = "Long scala calculation on UI thread canceled"
  }

  private object progress extends StandardProgressIndicator {
    val delegate = new EmptyProgressIndicator()

    private val counter = new AtomicLong()

    def timestamp: Long = counter.get()

    def start(): Unit = {
      counter.incrementAndGet()
      delegate.start()
    }

    def cancel(l: Long): Unit = {
      if (timestamp == l) delegate.cancel()
    }

    //to avoid long stacktraces in log and keep write actions
    def checkCanceled(): Unit = {
      if (isCanceled && !ApplicationManager.getApplication.isWriteAccessAllowed)
        throw pceInstance
    }

    //EmptyProgressIndicator is good enough, but it has final `checkCanceled()` method
    def cancel(): Unit = delegate.cancel()
    def isRunning: Boolean = delegate.isRunning
    def pushState(): Unit = delegate.pushState()
    def setIndeterminate(indeterminate: Boolean): Unit = delegate.setIndeterminate(indeterminate)
    def finishNonCancelableSection(): Unit = delegate.finishNonCancelableSection()
    def setModalityProgress(modalityProgress: ProgressIndicator): Unit = delegate.setModalityProgress(modalityProgress)
    def isCanceled: Boolean = delegate.isCanceled
    def isIndeterminate: Boolean = delegate.isIndeterminate
    def isModal: Boolean = delegate.isModal
    def setFraction(fraction: Double): Unit = delegate.setFraction(fraction)
    def stop(): Unit = delegate.stop()
    def getText: String = delegate.getText
    def setText(text: String): Unit = delegate.setText(text)
    def isPopupWasShown: Boolean = delegate.isPopupWasShown
    def setText2(text: String): Unit = delegate.setText2(text)
    def startNonCancelableSection(): Unit = delegate.startNonCancelableSection()
    def getModalityState: ModalityState = delegate.getModalityState
    def getFraction: Double = delegate.getFraction
    def popState(): Unit = delegate.popState()
    def getText2: String = delegate.getText2
    def isShowing: Boolean = delegate.isShowing
  }

}
