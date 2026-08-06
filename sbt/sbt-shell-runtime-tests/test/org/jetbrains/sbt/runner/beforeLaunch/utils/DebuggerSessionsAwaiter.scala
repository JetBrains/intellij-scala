package org.jetbrains.sbt.runner.beforeLaunch.utils

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.impl.{DebuggerManagerListener, DebuggerSession}
import com.intellij.openapi.project.Project
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.ui.AwaitTestUtils

import java.util.concurrent.CountDownLatch
import scala.concurrent.duration.Duration

/**
 * Waits for Java debugger sessions started by SBT run-configuration tests to be fully removed from the debugger manager.
 *
 * Debug-mode SBT task configurations can finish their lightweight mock process before the IntelliJ debugger finishes
 * asynchronous attach, log-capture, and disposal work. If the fixture tears the project down in that window, late
 * debugger tasks can try to register child disposables under already-disposed project or console disposables and the
 * test fails with unrelated disposer or PSI assertions.
 *
 * The awaiter subscribes before the run configuration is executed, so it observes even short-lived debugger sessions.
 * After the process terminates, the test waits until `DebuggerManagerEx` reports no remaining sessions, drains the
 * debugger UI queues, and only then allows normal fixture teardown to continue.
 */
private[beforeLaunch] trait DebuggerSessionsAwaiter {
  def awaitAllSessionsDetached(): Unit
}

/**
 * Creates debugger-session awaiters for test branches that start Java debug sessions.
 */
private[beforeLaunch] object DebuggerSessionsAwaiter {

  def subscribe(project: Project, timeout: Duration): DebuggerSessionsAwaiter = {
    val sessionCreatedLatch = new CountDownLatch(1)
    val sessionsDetachedLatch = new CountDownLatch(1)

    val debuggerManager = DebuggerManagerEx.getInstanceEx(project)

    def countDownIfNoSessions(): Unit = {
      if (debuggerManager.getSessions.isEmpty)
        sessionsDetachedLatch.countDown()
    }

    val connection = project.getMessageBus.connect()
    connection.subscribe(DebuggerManagerListener.TOPIC, new DebuggerManagerListener {
      override def sessionCreated(session: DebuggerSession): Unit =
        sessionCreatedLatch.countDown()

      override def sessionRemoved(session: DebuggerSession): Unit =
        countDownIfNoSessions()
    })

    if (!debuggerManager.getSessions.isEmpty)
      sessionCreatedLatch.countDown()

    () => {
      try {
        AwaitTestUtils.waitForLatchDispatchingAllEdtEvents(
          sessionCreatedLatch,
          timeout,
          "Timed out waiting for Java debugger session to start",
        )

        countDownIfNoSessions()

        AwaitTestUtils.waitForLatchDispatchingAllEdtEvents(
          sessionsDetachedLatch,
          timeout,
          "Timed out waiting for Java debugger sessions to detach",
        )
        drainDebuggerUiEvents()
      } finally {
        connection.disconnect()
      }
    }
  }

  private def drainDebuggerUiEvents(): Unit =
    for (_ <- 1 to 3) {
      invokeAndWait {
        // Debugger session removal can still leave EDT follow-up work from XDebugSessionTab and LogCapture console
        // registration. Drain both queues before test fixture teardown disposes the project and console tree.
        UIUtil.dispatchAllInvocationEvents()
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      }
    }
}
