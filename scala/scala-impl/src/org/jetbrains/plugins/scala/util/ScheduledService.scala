package org.jetbrains.plugins.scala.util

import java.util.concurrent.ScheduledExecutorService

import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.AppExecutorUtil

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/**
 * Periodically runs specified runnable
 */
abstract class ScheduledService(delay: FiniteDuration,
                                terminationTimeout: FiniteDuration = 3.seconds)
  extends Disposable {

  require(delay.length > 0, "non-positive delay")
  require(terminationTimeout.length > 0, "non-positive termination timeout")

  private var optionScheduler: Option[ScheduledExecutorService] = None

  /**
   * Start scheduling process.
   * @throws IllegalArgumentException if already scheduled
   */
  def startScheduling(): Unit =
    startSchedulingInternal()

  /**
   * Stop scheduling process.
   * The operation is idempotent.
   */
  def stopScheduling(): Unit =
    stopSchedulingInternal()

  def runnable: Runnable

  override def dispose(): Unit =
    stopSchedulingInternal()

  private def startSchedulingInternal(): Unit = synchronized {
    val schedulerName = this.getClass.getSimpleName
    if (optionScheduler.nonEmpty)
      throw new IllegalStateException(s"Scheduler '$schedulerName' is already scheduled")
    val newScheduler = AppExecutorUtil.createBoundedScheduledExecutorService(schedulerName, 1)
    newScheduler.scheduleWithFixedDelay(runnable, delay.length, delay.length, delay.unit)
    optionScheduler = Some(newScheduler)
  }

  private def stopSchedulingInternal(): Unit = synchronized {
    optionScheduler.foreach { scheduler =>
      scheduler.shutdownNow()
      scheduler.awaitTermination(terminationTimeout.length, terminationTimeout.unit)
    }
    optionScheduler = None
  }
}
