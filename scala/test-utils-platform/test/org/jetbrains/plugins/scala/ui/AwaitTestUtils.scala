package org.jetbrains.plugins.scala.ui

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import org.junit.Assert

import scala.concurrent.Future
import scala.concurrent.duration.Duration

object AwaitTestUtils {

  @RequiresEdt
  def waitFutureDispatchingAllEdtEvents[T](future: Future[T],  duration: Duration, attempts: Int = 100): T = {
    waitConditionedDispatchingAllEdtEvents(duration, attempts) { () => future.isCompleted }

    if (future.isCompleted)
      future.value.get.get
    else
      throw new IllegalStateException(s"Future is not completed after timeout: $duration")
  }

  @RequiresEdt
  def waitDispatchingAllEdtEvents(duration: Duration, attempts: Int = 100): Unit =
    waitConditionedDispatchingAllEdtEvents(duration, attempts)()

  @RequiresEdt
  def waitConditionedDispatchingAllEdtEvents(
    duration: Duration,
    attempts: Int = 100,
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
    attempts: Int = 100,
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
    attempts: Int = 100,
  )(condition: () => Boolean = () => false): Unit = {
    waitConditioned(duration, attempts)(condition)
    if (!condition()) {
      Assert.fail(failMessageBase + s" (in a $duration time frame)")
    }
  }
}
