package org.jetbrains.plugins.scala.util

import java.awt.Event
import java.awt.event.MouseEvent
import java.io.{File, IOException}
import java.lang.management.ManagementFactory
import java.text.SimpleDateFormat
import java.util
import java.util.Date
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{ScheduledFuture, TimeUnit}

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.concurrency.JobScheduler
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.ide.{ApplicationInitializedListener, IdeEventQueue}
import com.intellij.openapi.application.{ApplicationManager, ModalityState, PathManager, TransactionGuard}
import com.intellij.openapi.progress._
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiModificationTrackerImpl
import org.jetbrains.plugins.scala.caches.RecursionManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.UIFreezingGuard._

import scala.collection.mutable
import scala.util.control.NoStackTrace

/**
  * @author Nikolay.Tropin
  */
class UIFreezingGuard extends ApplicationInitializedListener {

  private val periodMs = 10

  override def componentsInitialized(): Unit = {
    if (pceEnabled) {
      JobScheduler.getScheduler.scheduleWithFixedDelay(cancelOnUserInput(), periodMs, periodMs, TimeUnit.MILLISECONDS)
    }
  }

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
  private var timeoutCount: Int = 0

  val edtResolveTimeoutKey = "scala.edt.resolve.timeout"
  private val defaultTimeout = -1

  Registry.addKey(edtResolveTimeoutKey,
    "Maximum time in millisecond to wait for reference resolve in scala file (-1 means no timeout)", defaultTimeout, false)

  private def isEdt: Boolean = ApplicationManager.getApplication.isDispatchThread

  //used in macro!
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

  def isUnderTimeout: Boolean = isEdt && timeoutCount > 0

  //Use with care! Can cause bugs if result is cached upper in the stack.
  def withTimeout[T](timeoutMs: Long, computation: => T, default: => T)(implicit projectContext: ProjectContext): T = {
    val application = ApplicationManager.getApplication
    if (!pceEnabled || !isEdt || application.isUnitTestMode || timeoutMs < 0) return computation

    try {
      timeoutCount += 1
      ProgressManager.getInstance().runProcess(computation, new TimeoutProgressIndicator(timeoutMs))
    } catch {
      case _: TimeoutException =>
        dumpUniqueStackTrace()

        RecursionManager.prohibitCaching()

        scheduleRehighlighting(1000L, TimeUnit.MILLISECONDS)

        default
    } finally {
      timeoutCount -= 1
    }
  }

  private val seenStackTraces = mutable.Set.empty[Int]

  private def dumpUniqueStackTrace(): Unit = {
    val stackTrace = new Exception().getStackTrace
    val hash = util.Arrays.hashCode(stackTrace.asInstanceOf[Array[AnyRef]])
    if (!seenStackTraces(hash)) {
      seenStackTraces.add(hash)
      dump(stackTrace)
    }
  }

  private def dump(stacktrace: Array[StackTraceElement]): Unit = {
    def formatDateTime(time: Long) = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date(time))

    val myLogDir: File = new File(PathManager.getLogPath)
    val scalaTimeoutDir = new File(myLogDir, "scalaEdtTimeouts")
    if (!scalaTimeoutDir.exists())
      FileUtil.createDirectory(scalaTimeoutDir)

    val startTime = ManagementFactory.getRuntimeMXBean.getStartTime
    val formatted = formatDateTime(startTime)

    val fileName = s"stacktraces-$formatted.txt"

    val file = new File(scalaTimeoutDir, fileName)
    val string = stacktrace.mkString("\n") + "\n\n\n"

    try {
      FileUtil.writeToFile(file, string, true)
    } catch {
      case _: IOException =>
    }
  }

  private var modCountIncrementFuture: ScheduledFuture[Unit] = null

  private def scheduleRehighlighting(delay: Long, timeUnit: TimeUnit)(implicit projectContext: ProjectContext): Unit = {
    def doIncrementCounter(): Unit = {
      //we don't use PsiModificationTracker anymore, but java support still rely on it
      //for example, TestFrameworks.detectApplicableFrameworks
      //let's increment it on timeout to invalidate wrong cached values
      val psiModTracker = PsiManager.getInstance(projectContext).getModificationTracker
      psiModTracker match {
        case modTracker: PsiModificationTrackerImpl => modTracker.incCounter()
        case _ =>
      }

      DaemonCodeAnalyzer.getInstance(projectContext).restart()
    }

    if (modCountIncrementFuture == null || modCountIncrementFuture.isDone) {
      modCountIncrementFuture =
        JobScheduler.getScheduler.schedule(() => invokeLater(doIncrementCounter()),
          delay, timeUnit)
    }
  }

  def resolveTimeoutMs: Int =
    if (isEdt && !isUnderTimeout) Registry.intValue(edtResolveTimeoutKey) else -1

  private def isWriteAction: Boolean = ApplicationManager.getApplication.isWriteAccessAllowed
  private def isTransaction: Boolean = TransactionGuard.getInstance().getContextTransaction != null

  private def isUnderProgress: Boolean = {
    val indicator = ProgressManager.getInstance().getProgressIndicator
    indicator != progress && !indicator.isInstanceOf[TimeoutProgressIndicator]
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

  private class TimeoutProgressIndicator(timeoutMs: Long, startTime: Long = System.currentTimeMillis())
    extends AbstractProgressIndicatorBase {

    override def isCanceled: Boolean = {
      val timeSinceStart = System.currentTimeMillis() - startTime

      timeSinceStart > timeoutMs || super.isCanceled
    }

    override def checkCanceled(): Unit = if (isCanceled && isCancelable && canInterrupt)
      throw new TimeoutException
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

    def checkCanceled(): Unit = {
      if (isCanceled && canInterrupt)
        throw UnfreezeException
    }

    //EmptyProgressIndicator is good enough, but it has final `checkCanceled()` method
    def cancel(): Unit = delegate.cancel()
    def isRunning: Boolean = delegate.isRunning
    def pushState(): Unit = delegate.pushState()
    def setIndeterminate(indeterminate: Boolean): Unit = delegate.setIndeterminate(indeterminate)
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
    def getModalityState: ModalityState = delegate.getModalityState
    def getFraction: Double = delegate.getFraction
    def popState(): Unit = delegate.popState()
    def getText2: String = delegate.getText2
    def isShowing: Boolean = delegate.isShowing
  }

  private object UnfreezeException extends ProcessCanceledException with NoStackTrace {
    override def getMessage: String = "Long scala calculation on UI thread canceled"
  }

  private class TimeoutException extends ProcessCanceledException with NoStackTrace {
    override def getMessage: String = "Computation cancelled with timeout"
  }
}