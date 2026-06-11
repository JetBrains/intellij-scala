package org.jetbrains.sbt.runner.debugger

import com.intellij.debugger.engine.RemoteDebugProcessHandler
import com.intellij.openapi.project.Project

import scala.concurrent.duration.FiniteDuration

private final class SbtRemoteDebugProcessHandler(project: Project) extends RemoteDebugProcessHandler(project) {
  /**
   * Provides a bounded fallback detach notification for the synthetic SBT shell debug run-configuration process.
   *
   * This is similar in spirit to `RemoteDebugProcessHandler.startNotify`, where the platform registers a
   * `processDetached` listener and then checks for a detach that may have happened before the listener was installed.
   * That platform protection runs during process start. SBT shell delegation also needs protection after an explicit
   * `detachProcess()` call, because the shell command can finish while the remote debug attach/detach lifecycle is still
   * settling. There is no platform method that waits for this process handler and publishes the missing detach event,
   * so this handler keeps the fallback local to the SBT shell debug flow.
   */
  def notifyDetachedIfNeededAfter(timeout: FiniteDuration): Unit = {
    val waitResult = waitFor(timeout.toMillis)
    if (waitResult || isProcessTerminated) {
      // no need to notify
    } else {
      notifyProcessDetached()
    }
  }
}
