package org.jetbrains.plugins.scala.util

import com.intellij.concurrency.JobScheduler
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.{ApplicationManager, ModalityState, TransactionGuard}
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress._
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.startup.ProjectActivity

import java.awt.Event
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{ScheduledFuture, TimeUnit}
import scala.annotation.nowarn
import scala.util.control.NoStackTrace

private final class UIFreezingGuard extends ProjectActivity {
  /**
   * An entry point for initialising the application-level service. This method is executed once per project, but
   * the application service initialisation is guaranteed to be executed exactly once, which is what matters to us.
   * Project activities are a useful tool for running a piece of code even when the plugin has been dynamically loaded
   * after the IDE has already been running for some time. This helps us to register our application-level service and
   * to initialise our recurring background task, regardless of how/when the Scala plugin was loaded.
   */
  override def execute(project: Project): Unit = {
    // Application-level services are initialised exactly once.
    ApplicationManager.getApplication.getService(classOf[UIFreezingGuard.AppService])
  }
}

object UIFreezingGuard {

  /**
   * Schedules a recurring task (only once) which runs on a background thread every 300 milliseconds and checks the IDE
   * global event queue for unprocessed user input events.
   * If such events are detected, it issues a cancellation, to hopefully stop the long-running resolve computation which
   * is blocking the UI thread.
   */
  @Service(Array(Service.Level.APP))
  private final class AppService extends Disposable {
    private final val periodMs = 300
    private var periodicTask: ScheduledFuture[_] =
      if (pceEnabled)
        JobScheduler.getScheduler.scheduleWithFixedDelay(() => cancelOnUserInput(), periodMs, periodMs, TimeUnit.MILLISECONDS)
      else
        null

    override def dispose(): Unit = {
      if (periodicTask ne null) {
        periodicTask.cancel(false)
        periodicTask = null
      }
    }
  }

  private def cancelOnUserInput(): Unit = {
    val timestamp = progress.timestamp
    if (progress.isRunning && hasPendingUserInput) {
      progress.cancel(timestamp)
    }
  }

  private val pceEnabled = System.getProperty("idea.ProcessCanceledException") != "disabled"

  private var isGuarded: Boolean = false

  private def isEdt: Boolean = ApplicationManager.getApplication.isDispatchThread

  /**
   * Cooperatively called by the caching methods to ensure that any long-running resolve which is executed
   * on the UI thread can be preempted by throwing a [[ProcessCanceledException]].
   */
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

  private def hasModalityState: Boolean = ModalityState.current() != ModalityState.nonModal()

  private def canInterrupt: Boolean = !isWriteAction && !isTransaction && !isUnderProgress && !hasModalityState

  /**
   * Creates and saves a thread-dump to disk for any freeze longer than 1 second.
   *
   * @note This method is very slow and actually makes the freeze worse, as perceived by the user.
   *       But we keep it around because it is a good way of reporting long-running resolve computations on the UI thread,
   *       which we need to address.
   */
  private def dumpThreads(ms: Long): Unit = {
    val threshold = 1000
    if (ms > threshold) {
      PerformanceWatcher.getInstance().dumpThreads("scalaEdtFreezing/", false, false)
    }
  }

  /**
   * Checks that the global IDE event queue contains any of the events we are interested in (currently keyboard and
   * mouse events).
   * If these events are in the IDE event queue and have not already been processed by the UI thread,
   * we assume that the UI thread is blocked by a long-running resolve computation.
   */
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
