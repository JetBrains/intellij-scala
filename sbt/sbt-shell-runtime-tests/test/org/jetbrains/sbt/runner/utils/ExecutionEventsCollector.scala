package org.jetbrains.sbt.runner.utils

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.{ExecutionListener, RunnerAndConfigurationSettings}
import org.jetbrains.plugins.scala.ui.AwaitTestUtils
import org.jetbrains.sbt.runner.utils.ExecutionEventsCollector.ExecutionEvent

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

private[runner] final class ExecutionEventsCollector(
  settings: RunnerAndConfigurationSettings,
  eventCounter: AtomicInteger = new AtomicInteger(),
) extends ExecutionListener {

  private val events = mutable.ArrayBuffer.empty[ExecutionEvent]
  private val terminationRecorded = new CountDownLatch(1)

  def eventsSnapshot: Vector[ExecutionEvent] =
    events.synchronized {
      events.toVector
    }

  /**
   * Waits until this listener has recorded `processTerminated` for the observed configuration.
   *
   * Execution tests subscribe both this collector and [[RunConfigurationExecutionObserver]]
   * to `ExecutionManager.EXECUTION_TOPIC`. The observer releases its own
   * `awaitSuccessfulTermination` wait in its `processTerminated` callback, but delivery
   * of that same event to this collector can still be pending. For example, the test
   * thread can wake and snapshot events ending at `processTerminating` before this
   * collector appends `processTerminated`. This latch makes the snapshot wait for
   * the collector's callback rather than the observer's callback.
   */
  def awaitProcessTerminated(timeout: FiniteDuration): Unit =
    AwaitTestUtils.waitForLatchDispatchingAllEdtEvents(
      terminationRecorded,
      timeout,
      s"Timed out waiting for '${settings.getName}' processTerminated event",
    )

  override def processStartScheduled(executorId: String, env: ExecutionEnvironment): Unit =
    record(env, "processStartScheduled")

  override def processStarting(executorId: String, env: ExecutionEnvironment): Unit =
    record(env, "processStarting")

  override def processStarting(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler): Unit =
    record(env, "processStartingWithHandler", Some(handler))

  override def processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler): Unit =
    record(env, "processStarted", Some(handler))

  override def processTerminating(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler): Unit =
    record(env, "processTerminating", Some(handler))

  override def processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int): Unit = {
    if (isObservedEnvironment(env)) {
      record(env, "processTerminated", Some(handler))
      terminationRecorded.countDown()
    }
  }

  private def record(
    env: ExecutionEnvironment,
    name: String,
    processHandler: Option[ProcessHandler] = None,
  ): Unit = {
    if (isObservedEnvironment(env)) {
      events.synchronized {
        events += ExecutionEvent(eventCounter.incrementAndGet(), name, processHandler, runnerId(env))
      }
    }
  }

  private def runnerId(env: ExecutionEnvironment): Option[String] =
    Option(env.getRunner).map(_.getRunnerId)

  private def isObservedEnvironment(env: ExecutionEnvironment): Boolean = {
    val environmentSettings = env.getRunnerAndConfigurationSettings
    environmentSettings != null && environmentSettings.getUniqueID == settings.getUniqueID
  }
}

object ExecutionEventsCollector {

  final case class ExecutionEvent(
    order: Int,
    name: String,
    processHandler: Option[ProcessHandler],
    runnerId: Option[String],
  )
}
