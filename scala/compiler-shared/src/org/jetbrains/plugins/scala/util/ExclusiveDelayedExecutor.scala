package org.jetbrains.plugins.scala.util

import java.util.concurrent.ScheduledThreadPoolExecutor

import scala.concurrent.duration.FiniteDuration

class ExclusiveDelayedExecutor {

  private val scheduler = new ScheduledThreadPoolExecutor(1)

  /**
   * Execute if execution not scheduled already.
   *
   * @return ''true'' if action will be executed ''false'' if already executed.
   */
  def execute(delay: FiniteDuration)
             (action: => Unit): Boolean =
    if (scheduler.getQueue.isEmpty) {
      val runnable: Runnable = () => action
      scheduler.schedule(runnable, delay.length, delay.unit)
      true
    } else {
      false
    }
}
