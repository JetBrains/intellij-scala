package org.jetbrains.plugins.scala.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.scala.extensions.ToNullSafe

import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.FiniteDuration

class KeyedRescheduledExecutor[K](name: String,
                                  parentDisposable: Disposable,
                                  cancelPredicate: (K, K) => Boolean)
  extends Disposable {

  Disposer.register(parentDisposable, this)

  private val lastScheduledTask = new AtomicReference[(ScheduledFuture[_], K)]
  private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService(name, 1)

  def schedule(delay: FiniteDuration, key: K)
              (action: => Unit): Unit = {
    val needToSchedule = lastScheduledTask.get.nullSafe.forall { case (task, taskKey) =>
      if (cancelPredicate(taskKey, key)) {
        cancelLastInternal(task)
        true
      } else {
        false
      }
    }

    if (needToSchedule) {
      val runnable: Runnable = { () =>
        action
        lastScheduledTask.set(null)
      }
      val future = scheduler.schedule(runnable, delay.length, delay.unit)
      lastScheduledTask.set((future, key))
    }
  }

  def cancel(): Unit =
    lastScheduledTask.get.nullSafe.foreach { case (task, _) =>
      cancelLastInternal(task)
    }

  private def cancelLastInternal(task: ScheduledFuture[_]): Unit = {
    task.cancel(false)
    lastScheduledTask.set(null)
  }

  override def dispose(): Unit =
    scheduler.shutdownNow()
}

class RescheduledExecutor(name: String, parentDisposable: Disposable)
  extends KeyedRescheduledExecutor[Unit](name, parentDisposable, (_, _) => true) {

  def schedule(delay: FiniteDuration)
              (action: => Unit): Unit =
    schedule(delay, ())(action)
}