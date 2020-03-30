package org.jetbrains.plugins.scala.util

import com.intellij.util.ui.UIUtil
import javax.swing.SwingUtilities

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.Try

object TestUtilsScala {

  /**
   * Unit tests are run in EDT, so we can't just use [[scala.concurrent.Await.result]] - it will block EDT and lead to
   * all EDT events starving. So no code in "invokeLater" or "invokeLaterAndWait" etc... will be executed.
   * We must periodically flush EDT events to workaround this.
   */
  def awaitWithoutUiStarving[T](
    future: Future[T],
    duration: Duration,
    sleepInterval: Duration = 100.milliseconds
  ): Option[Try[T]] = {
    var timeSpent: Duration = Duration.Zero

    while (!future.isCompleted && (timeSpent < duration)) {
      if (SwingUtilities.isEventDispatchThread) {
        UIUtil.dispatchAllInvocationEvents()
      }
      Thread.sleep(sleepInterval.toMillis)
      timeSpent = timeSpent.plus(sleepInterval)
    }

    future.value
  }

}
