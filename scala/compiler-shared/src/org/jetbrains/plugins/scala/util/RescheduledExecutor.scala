package org.jetbrains.plugins.scala.util

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ScheduledFuture, ScheduledThreadPoolExecutor}

import com.intellij.util.concurrency.AppExecutorUtil

import scala.concurrent.duration.FiniteDuration

class RescheduledExecutor(name: String,
                          mayInterruptIfRunning: Boolean = false) {

  import RescheduledExecutor.IgnoreKey
  
  private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService(name, 1)

  private val lastScheduledTask = new AtomicReference[(ScheduledFuture[_], String)]

  /**
   * Schedules action with specified delay.
   * If already scheduled, than task will be rescheduled.
   */
  def schedule(delay: FiniteDuration)
              (action: => Unit): Unit =
    schedule(delay, IgnoreKey)(action)

  /**
   * Cancels last scheduled task.
   */
  def cancelLast(): Unit =
    cancelLast(IgnoreKey)

  private def cancelLast(key: String): Unit = {
    lastScheduledTask.get match {
      case (task, taskKey) if taskKey == key =>
        task.cancel(mayInterruptIfRunning)
      case _ =>
    }
    lastScheduledTask.set(null)
  }
  
  /**
   * @param key this allows us to queue several worksheets compilation (on project opening, for example)
   *            while disallowing several compilation requests for a single worksheet
   *            (which can lead to fast error blinking on worksheet editing)
   */
  def schedule(delay: FiniteDuration, key: String)
              (action: => Unit): Unit = {
    cancelLast(key)

    val runnable: Runnable = () => action
    val future = scheduler.schedule(runnable, delay.length, delay.unit)

    lastScheduledTask.set((future, key))
  }
}

object RescheduledExecutor {
  
  private final val IgnoreKey = "ignore_key"
}
