package org.jetbrains.plugins.scala.util

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ScheduledFuture, ScheduledThreadPoolExecutor}

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil

import scala.concurrent.duration.FiniteDuration

class RescheduledExecutor(val name: String,
                          parentDisposable: Disposable) extends Disposable {

  Disposer.register(parentDisposable, this)

  private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService(name, 1)

  private val lastScheduledTask = new AtomicReference[ScheduledFuture[_]]

  /**
   * Schedules action with specified delay.
   * If already scheduled, than task will be rescheduled.
   */
  def schedule(delay: FiniteDuration)
              (action: => Unit): Unit = {
      cancelLast()
      val runnable: Runnable = () => action
      val future = scheduler.schedule(runnable, delay.length, delay.unit)
      lastScheduledTask.set(future)
    }

  /**
   * Cancels last scheduled task.
   */
  def cancelLast(): Unit = {
    Option(lastScheduledTask.get).foreach(_.cancel(/*mayInterruptIfRunning*/ false))
    lastScheduledTask.set(null)
  }

  override def dispose(): Unit = {
    scheduler.shutdownNow()
    lastScheduledTask.set(null)
  }
}
