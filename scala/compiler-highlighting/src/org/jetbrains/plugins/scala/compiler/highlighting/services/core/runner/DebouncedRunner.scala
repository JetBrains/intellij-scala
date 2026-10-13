package org.jetbrains.plugins.scala.compiler.highlighting.services.core.runner

import com.intellij.openapi.Disposable

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ScheduledExecutorService, ScheduledFuture, TimeUnit}
import scala.concurrent.duration.{DurationInt, FiniteDuration}

trait DebouncedRunner(executor: ScheduledExecutorService,
                      delay: FiniteDuration = 300.milliseconds) extends TaskRunner, Disposable:

  override def wakeUp(): Unit = scheduleNextTask(delay)

  /** The currently ticking timer. */
  private val scheduledTask = new AtomicReference[ScheduledFuture[?]](null)
  
  /**
   * Schedules a task in `delay`. Unconditionally cancels any previously
   * ticking timer, causing the delay to push back (debounce).
   */
  private def scheduleNextTask(delay: FiniteDuration): Unit =
    val future = executor.schedule(task(), delay.toMillis.max(0L), TimeUnit.MILLISECONDS)
    val previous = scheduledTask.getAndSet(future)
    if previous ne null then previous.cancel(false)
  
  override def dispose(): Unit =
    executor.shutdown()
    val task = scheduledTask.getAndSet(null)
    if task ne null then task.cancel(true)
