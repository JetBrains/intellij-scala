package org.jetbrains.plugins.scala.util

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ScheduledFuture, ScheduledThreadPoolExecutor}

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil

import scala.concurrent.duration.FiniteDuration

class RescheduledExecutor(val name: String,
                          parentDisposable: Disposable) extends Disposable {

  import RescheduledExecutor.IgnoreKey

  Disposer.register(parentDisposable, this)

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
        task.cancel(/*mayInterruptIfRunning*/ false)
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

  override def dispose(): Unit = {
    scheduler.shutdownNow()
    lastScheduledTask.set(null)
  }
}

object RescheduledExecutor {

  private final val IgnoreKey = "ignore_key"
}
