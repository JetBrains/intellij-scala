package org.jetbrains.plugins.scala.util

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ScheduledFuture, ScheduledThreadPoolExecutor}

import scala.concurrent.duration.FiniteDuration

class RescheduledExecutor(mayInterruptIfRunning: Boolean = false) {

  private val scheduler = new ScheduledThreadPoolExecutor(1)
  private val lastScheduledTask = new AtomicReference[ScheduledFuture[_]]

  /**
   * Schedule action with specified delay.
   * If already scheduled, than task will be rescheduled.
   */
  def schedule(delay: FiniteDuration)
              (action: => Unit): Unit = {
    Option(lastScheduledTask.get).foreach(_.cancel(mayInterruptIfRunning))
    val runnable: Runnable = () => action
    val future = scheduler.schedule(runnable, delay.length, delay.unit)
    lastScheduledTask.set(future)
  }
}
