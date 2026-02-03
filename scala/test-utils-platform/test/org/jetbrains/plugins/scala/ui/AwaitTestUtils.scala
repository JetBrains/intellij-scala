package org.jetbrains.plugins.scala.ui

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil

import scala.concurrent.duration.Duration

object AwaitTestUtils {

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
}
