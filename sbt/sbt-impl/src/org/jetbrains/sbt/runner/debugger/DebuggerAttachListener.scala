package org.jetbrains.sbt.runner.debugger

import com.intellij.debugger.engine.{DebugProcessAdapterImpl, DebugProcessImpl}
import com.intellij.execution.ExecutionException
import org.jetbrains.sbt.SbtBundle

import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.concurrent.duration.FiniteDuration

private final class DebuggerAttachListener extends DebugProcessAdapterImpl {
  private val signal = new CountDownLatch(1)

  override def processAttached(process: DebugProcessImpl): Unit =
    signal.countDown()

  override def processDetached(process: DebugProcessImpl, closedByUser: Boolean): Unit =
    signal.countDown()

  def awaitSignal(timeout: FiniteDuration): Unit = {
    val isSignalled = signal.await(timeout.toMillis, TimeUnit.MILLISECONDS)
    if (!isSignalled) {
      throw new ExecutionException(SbtBundle.message("timed.out.waiting.for.sbt.shell.debugger.to.attach"))
    }
  }
}

private object DebuggerAttachListener {

  /**
   * Waits for a delayed debugger attach signal unless the debug process is already attached.
   *
   * This is closest to platform debug-process listener usage in
   * `com.intellij.debugger.impl.DebuggerSession.MyDebugProcessListener` and `DebugProcessImpl.attachVirtualMachine`.
   * The platform owns the attach flow, but it does not expose a blocking "wait until processAttached was delivered"
   * primitive. SBT shell delegation needs that explicit wait because `DelayedRemoteConnection` can return after the VM
   * is obtained but before `DebugProcessImpl` commits the VM and switches to the attached state.
   */
  @throws[ExecutionException]
  def awaitAttachSignalIfNeeded(debugProcess: DebugProcessImpl, timeout: FiniteDuration): AttachWaitResult = {
    if (debugProcess.isAttached) {
      return AttachWaitResult.AlreadyAttached
    }

    val listener = new DebuggerAttachListener
    debugProcess.addDebugProcessListener(listener)
    try {
      if (debugProcess.isAttached) {
        return AttachWaitResult.AlreadyAttached
      }

      listener.awaitSignal(timeout)

      if (!debugProcess.isAttached) {
        throw new ExecutionException(SbtBundle.message("sbt.shell.debugger.detached.before.the.remote.vm.was..."))
      }

      AttachWaitResult.AttachedAfterWaiting
    } finally {
      debugProcess.removeDebugProcessListener(listener)
    }
  }
}
