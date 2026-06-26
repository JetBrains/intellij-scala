package org.jetbrains.plugins.scala.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import org.junit.Assert

import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

/**
 * Also see [[com.intellij.testFramework.PlatformTestUtil#waitForPromise]]
 */
object AwaitTestUtils {

  private val DefaultAttempts = 100

  @RequiresEdt
  def waitFutureDispatchingAllEdtEvents[T](future: Future[T],  duration: Duration, attempts: Int = DefaultAttempts): T = {
    waitConditionedDispatchingAllEdtEvents(duration, attempts) { () => future.isCompleted }

    if (future.isCompleted)
      future.value.get.get
    else
      throw new IllegalStateException(s"Future is not completed after timeout: $duration")
  }

  @RequiresEdt
  def waitDispatchingAllEdtEvents(duration: Duration, attempts: Int = DefaultAttempts): Unit =
    waitConditionedDispatchingAllEdtEvents(duration, attempts)()

  def waitForLatchDispatchingAllEdtEvents(
    latch: CountDownLatch,
    duration: Duration,
    failMessage: String,
    earlyBreakCondition: () => Boolean = () => false,
  ): Unit = {
    val deadline = System.nanoTime() + duration.toNanos
    while (!earlyBreakCondition() && !latch.await(10, TimeUnit.MILLISECONDS)) {
      if (ApplicationManager.getApplication.isDispatchThread) {
        UIUtil.dispatchAllInvocationEvents()
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      }
      if (System.nanoTime() > deadline) {
        Assert.fail(failMessage)
      }
    }
  }

  @RequiresEdt
  def waitConditionedDispatchingAllEdtEvents(
    duration: Duration,
    attempts: Int = DefaultAttempts,
  )(earlyBreakCondition: () => Boolean = () => false): Unit = {
    val timeout = duration.toMillis
    var idx = 0
    val sleepTime = timeout / attempts

    while (idx < attempts && !earlyBreakCondition()) {
      UIUtil.dispatchAllInvocationEvents()
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      Thread.sleep(sleepTime)
      idx += 1
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  def waitConditioned(
    duration: Duration,
    attempts: Int = DefaultAttempts,
  )(earlyBreakCondition: () => Boolean = () => false): Unit = {
    val timeout = duration.toMillis
    var idx = 0
    val sleepTime = timeout / attempts

    while (idx < attempts && !earlyBreakCondition()) {
      Thread.sleep(sleepTime)
      idx += 1
    }
  }

  def waitForConditionOrFail(
    duration: Duration,
    failMessageBase: String,
    attempts: Int = DefaultAttempts,
  )(condition: () => Boolean = () => false): Unit = {
    waitConditioned(duration, attempts)(condition)
    if (!condition()) {
      Assert.fail(failMessageBase + s" (in a $duration time frame)")
    }
  }

  @RequiresEdt
  def waitForConditionDispatchingEdtEventsOrFail(
    duration: Duration,
    failMessageBase: String,
    attempts: Int = DefaultAttempts,
  )(condition: () => Boolean): Unit = {
    waitConditionedDispatchingAllEdtEvents(duration, attempts)(condition)
    if (!condition()) {
      Assert.fail(failMessageBase + s" (in a $duration time frame)")
    }
  }

  def waitFutureOrFail[T](
    future: Future[T],
    duration: Duration,
    actionDescription: String,
    attempts: Int = DefaultAttempts,
  ): T = {
    waitForConditionOrFail(duration, s"Timed out while $actionDescription", attempts)(() => future.isCompleted)

    future.value match {
      case Some(Success(value)) =>
        value
      case Some(Failure(exception)) =>
        throw new AssertionError(s"Failed while $actionDescription. Cause: ${exception.getClass.getName}: ${exception.getMessage}")
      case None =>
        throw new AssertionError(s"Future was not completed after waiting while $actionDescription")
    }
  }
}
