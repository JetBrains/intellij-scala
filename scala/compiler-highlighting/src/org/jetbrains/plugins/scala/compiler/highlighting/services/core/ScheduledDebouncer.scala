package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer

import java.util.concurrent.{ConcurrentHashMap, ScheduledExecutorService, ScheduledFuture, TimeUnit}
import scala.compiletime.uninitialized
import scala.concurrent.duration.FiniteDuration

/**
 * A [[Debouncer]] that gives every key its own timer, all driven by the given scheduler.
 * Actions are therefore expected not to block. Disposal is tied to `parent`; once disposed, nothing
 * further is scheduled.
 */
private[highlighting] class ScheduledDebouncer[K](
  delay: => FiniteDuration,
  executor: ScheduledExecutorService,
  parent: Disposable
) extends Debouncer[K] with Disposable {

  /** The action still waiting for each key, if any. */
  private val pending = new ConcurrentHashMap[K, Task]()

  @volatile private var disposed = false

  Disposer.register(parent, this)

  override def queue(key: K)(action: => Unit): Unit = {
    if (!disposed) {
      pending.compute(key, (_, previous) => {
        if (previous ne null) previous.cancel()
        new Task(key, () => action).schedule()
      })
    }
  }

  override def cancel(key: K): Unit = Option(pending.remove(key)).foreach(_.cancel())


  override def dispose(): Unit = {
    disposed = true
    pending.values().forEach(_.cancel())
    pending.clear()
    executor.shutdown()
  }

  /**
   * One pending invocation. It drops itself from `pending` before running, so a notification arriving afterwards
   * opens a fresh window instead of cancelling a task that has already started. A task that is cancelled after it
   * started still runs.
   */
  private final class Task(key: K, action: () => Unit) extends Runnable {

    @volatile private var future: ScheduledFuture[?] = uninitialized
    private val Log = Logger.getInstance(classOf[Task])

    def schedule(): Task = {
      future = executor.schedule(this, delay.toMillis, TimeUnit.MILLISECONDS)
      this
    }

    def cancel(): Unit = {
      val scheduled = future
      if (scheduled ne null) {
        Log.debug(s"job canceled and debounced for $key")
        scheduled.cancel(false)
      }
    }

    override def run(): Unit = {
      pending.remove(key, this)
      action()
    }
  }
}