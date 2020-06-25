package org.jetbrains.plugins.scala.worksheet.integration.util

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.UIUtil

import scala.concurrent.duration.Duration

private[integration] object MyUiUtils {

  def wait(duration: Duration, attempts: Int = 100) : Unit =
    waitConditioned(duration, attempts)()

  def waitConditioned(duration: Duration, attempts: Int = 100)(earlyBreakCondition: () => Boolean = () => false) : Unit = {
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
