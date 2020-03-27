package org.jetbrains.plugins.scala.util

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ScheduledFuture, ScheduledThreadPoolExecutor}

import com.intellij.util.concurrency.AppExecutorUtil

import scala.concurrent.duration.FiniteDuration

class RescheduledExecutor(name: String,
                          mayInterruptIfRunning: Boolean = false) {

  private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService(name, 1)

  private val lastScheduledTask = new AtomicReference[(ScheduledFuture[_], String)]

  /**
   * Schedule action with specified delay.
   * If already scheduled, than task will be rescheduled.
   */
  def schedule(delay: FiniteDuration)
              (action: => Unit): Unit =
    schedule(delay, "ignore_key")(action)

  /**
   * @param key this allows us to queue several worksheets compilation (on project opening, for example)
   *            while disallowing several compilation requests for a single worksheet
   *            (which can lead to fast error blinking on worksheet editing)
   */
  def schedule(delay: FiniteDuration, key: String)
              (action: => Unit): Unit = {
    lastScheduledTask.get match {
      case (task, taskKey) if taskKey == key =>
        task.cancel(mayInterruptIfRunning)
      case _ =>
    }

    val runnable: Runnable = () => action
    val future = scheduler.schedule(runnable, delay.length, delay.unit)

    lastScheduledTask.set((future, key))
  }
}
