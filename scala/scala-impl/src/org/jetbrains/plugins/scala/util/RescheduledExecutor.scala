package org.jetbrains.plugins.scala.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.scala.extensions.ToNullSafe
import org.jetbrains.plugins.scala.util.RescheduledExecutor.Action

import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.FiniteDuration

class RescheduledExecutor(name: String, parentDisposable: Disposable)
  extends Disposable {

  Disposer.register(parentDisposable, this)

  private val lastScheduledTask = new AtomicReference[ScheduledFuture[_]]
  private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService(name, 1)

  /**
   * Schedules the action with specified delay.
   * If there is a scheduled task it will be cancelled.
   * If action conditions is false, than the action will be rescheduled with the same delay.
   */
  def schedule(delay: FiniteDuration, action: Action): Unit = {
    cancelInternal()
    val runnable: Runnable = { () =>
      if (action.condition)
        action.perform()
      else
        schedule(delay, action)
      lastScheduledTask.set(null)
    }
    val future = scheduler.schedule(runnable, delay.length, delay.unit)
    lastScheduledTask.set(future)
  }

  final def schedule(delay: FiniteDuration)
                    (action: => Unit): Unit =
    schedule(delay, new Action {
      override def perform(): Unit = action
      override def condition: Boolean = true
    })

  def cancel(): Unit =
    cancelInternal()

  override def dispose(): Unit = {
    scheduler.shutdownNow()
    lastScheduledTask.set(null)
  }

  private def cancelInternal(): Unit =
    lastScheduledTask.get.nullSafe.foreach { task =>
      task.cancel(false)
      lastScheduledTask.set(null)
    }
}

object RescheduledExecutor {

  trait Action {
    def perform(): Unit
    def condition: Boolean
  }
}