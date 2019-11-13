package org.jetbrains.plugins.scala.worksheet.integration.util

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.UIUtil

import scala.concurrent.duration.Duration

private[integration] object MyUiUtils {

  def wait(duration: Duration, attempts: Int = 100)(implicit d: DummyImplicit) : Unit =
    waitConditioned(duration, attempts)()

  def waitConditioned(duration: Duration, attempts: Int = 100)(earlyBreakCondition: () => Boolean = () => false) : Unit = {
    val timeout = duration.toMillis
    (1 to attempts)
      .takeWhile(_ => !earlyBreakCondition())
      .foreach { _ =>
        UIUtil.dispatchAllInvocationEvents()
        Thread.sleep(timeout / attempts)
      }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }
}
