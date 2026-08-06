package org.jetbrains.sbt.runner.debugger

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.execution.ExecutionException
import org.jetbrains.sbt.SbtBundle

import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.concurrent.duration.FiniteDuration

private final class DebuggerCommandListener extends DebuggerCommandImpl(PrioritizedTask.Priority.NORMAL) {
  private val completed = new CountDownLatch(1)

  override protected def action(): Unit =
    completed.countDown()

  override protected def commandCancelled(): Unit =
    completed.countDown()

  def awaitCompletion(timeout: FiniteDuration): Unit = {
    val isReady = completed.await(timeout.toMillis, TimeUnit.MILLISECONDS)
    if (!isReady) {
      throw new ExecutionException(SbtBundle.message("timed.out.waiting.for.sbt.shell.debugger.to.finish..."))
    }
  }
}

private object DebuggerCommandListener {
  /**
   * Schedules a no-op debugger command and waits until the debugger manager thread reaches it.
   *
   * This follows the same primitive used throughout the platform debugger:
   * `DebugProcessImpl.getManagerThread.schedule(new DebuggerCommandImpl(...))`.
   * We keep a custom wrapper because the public `DebuggerManagerThread` API has no bounded "drain until this command"
   * operation, and the concrete manager-thread helpers do not give this call site the timeout and cancellation handling
   * needed for SBT shell debug startup.
   */
  @throws[ExecutionException]
  def awaitScheduledCommand(debugProcess: DebugProcessImpl, timeout: FiniteDuration): Unit = {
    val command = new DebuggerCommandListener
    val scheduled = debugProcess.getManagerThread.schedule(command)
    if (!scheduled) {
      throw new ExecutionException(SbtBundle.message("sbt.shell.debugger.manager.thread.is.not.available"))
    }
    command.awaitCompletion(timeout)
  }
}
