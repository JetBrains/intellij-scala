package org.jetbrains.plugins.scala.util

import com.intellij.concurrency.JobScheduler
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.{ApplicationManager, ModalityState, TransactionGuard}
import com.intellij.openapi.progress._
import org.jetbrains.plugins.scala.components.RunOnceStartupActivity
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.util.UIFreezingGuard._

import java.awt.Event
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{ScheduledFuture, TimeUnit}
import scala.annotation.{nowarn, unused}
import scala.util.control.NoStackTrace

class UIFreezingGuard extends RunOnceStartupActivity {

  private val periodMs = 10
  private var periodicTask: ScheduledFuture[_] = _

  override def doRunActivity(): Unit = {
    if (pceEnabled) {
      periodicTask =
        JobScheduler.getScheduler.scheduleWithFixedDelay(() => cancelOnUserInput(), periodMs, periodMs, TimeUnit.MILLISECONDS)
    }
  }

  override protected def doCleanup(): Unit = periodicTask.cancel(false)

  private def cancelOnUserInput(): Unit = {
    val timestamp = progress.timestamp
    if (progress.isRunning && hasPendingUserInput) {
      progress.cancel(timestamp)
    }
  }
}

object UIFreezingGuard {

  private val pceEnabled = System.getProperty("idea.ProcessCanceledException") != "disabled"

  private var isGuarded: Boolean = false

  private def isEdt: Boolean = ApplicationManager.getApplication.isDispatchThread

  @unused("used by CachedMacroUtil")
  def withResponsibleUI[T](body: => T): T = {
    if (!isAlreadyGuarded && pceEnabled) {
      val start = System.currentTimeMillis()
      try {
        isGuarded = true
        val progressManager = ProgressManager.getInstance()

        if (canInterrupt) {

          if (hasPendingUserInput)
            throw UnfreezeException

          progressManager.runProcess(body, progress)
        }
        else
          body
      } finally {
        isGuarded = false
        dumpThreads(System.currentTimeMillis() - start)
      }
    }
    else body
  }

  //body should have withResponsibleUI call inside
  def withDefaultValue[T](default: T)(body: T): T = {
    if (isEdt && hasPendingUserInput) default
    else {
      try body
      catch {
        case UnfreezeException => default
      }
    }
  }

  //used in macro to reduce number of `withResponsibleUI` calls in the stacktrace
  def isAlreadyGuarded: Boolean = isEdt && isGuarded || !isEdt

  private def isWriteAction: Boolean = ApplicationManager.getApplication.isWriteAccessAllowed
  private def isTransaction: Boolean = {
    (TransactionGuard.getInstance().getContextTransaction != null): @nowarn("cat=deprecation")
  }

  private def isUnderProgress: Boolean = {
    val indicator = ProgressManager.getInstance().getProgressIndicator
    indicator != progress
  }
  private def hasModalityState: Boolean = ModalityState.current() != ModalityState.NON_MODAL

  private def canInterrupt: Boolean = !isWriteAction && !isTransaction && !isUnderProgress && !hasModalityState

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

  private object progress extends StandardProgressIndicator {
    val delegate = new EmptyProgressIndicator()

    private val counter = new AtomicLong()

    def timestamp: Long = counter.get()

    override def start(): Unit = {
      counter.incrementAndGet()
      delegate.start()
    }

    def cancel(l: Long): Unit = {
      if (timestamp == l) delegate.cancel()
    }

    override def checkCanceled(): Unit = {
      if (isCanceled && canInterrupt)
        throw UnfreezeException
    }

    //EmptyProgressIndicator is good enough, but it has final `checkCanceled()` method
    override def cancel(): Unit                                                 = delegate.cancel()
    override def isRunning: Boolean                                             = delegate.isRunning
    override def pushState(): Unit                                              = delegate.pushState()
    override def setIndeterminate(indeterminate: Boolean): Unit                 = delegate.setIndeterminate(indeterminate)
    override def setModalityProgress(modalityProgress: ProgressIndicator): Unit = delegate.setModalityProgress(modalityProgress)
    override def isCanceled: Boolean                                            = delegate.isCanceled
    override def isIndeterminate: Boolean                                       = delegate.isIndeterminate
    override def isModal: Boolean                                               = delegate.isModal
    override def setFraction(fraction: Double): Unit                            = delegate.setFraction(fraction)
    override def stop(): Unit                                                   = delegate.stop()
    override def getText: String                                                = delegate.getText
    override def setText(text: String): Unit                                    = delegate.setText(text)
    override def isPopupWasShown: Boolean                                       = delegate.isPopupWasShown
    override def setText2(text: String): Unit                                   = delegate.setText2(text)
    override def getModalityState: ModalityState                                = delegate.getModalityState
    override def getFraction: Double                                            = delegate.getFraction
    override def popState(): Unit                                               = delegate.popState()
    override def getText2: String                                               = delegate.getText2
    override def isShowing: Boolean                                             = delegate.isShowing
  }

  private object UnfreezeException extends ProcessCanceledException with NoStackTrace {
    override def getMessage: String = "Long scala calculation on UI thread canceled"
  }
}